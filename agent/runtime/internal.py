"""Private SDK facade used by CareerMate's existing Java model/prompt clients."""
import asyncio
import copy
import json
import os
import uuid
from types import SimpleNamespace

from contextlib import asynccontextmanager
from opentelemetry import trace
from opentelemetry.trace.propagation.tracecontext import TraceContextTextMapPropagator
from starlette.responses import JSONResponse, StreamingResponse
from starlette.routing import Route
from keel.context import Context
from keel.http_pool import use_transport
from keel.llm.client import ChatResult
from keel.protocol.events import TokenEvent
from keel.protocol.errors import ErrorCode, KeelError
from keel.prompts import TrustedMessage
from keel.tracing.otel import configure_from_env


def install_internal(app, agent):
    provider = configure_from_env()
    tracer = provider.get_tracer("careermate.native") if provider else trace.get_tracer("careermate.native")
    lifecycle = app.router.lifespan_context

    @asynccontextmanager
    async def lifespan(application):
        try:
            async with lifecycle(application):
                yield
        finally:
            if provider:
                provider.shutdown()
    app.router.lifespan_context = lifespan

    @asynccontextmanager
    async def context(request):
        parent = TraceContextTextMapPropagator().extract(dict(request.headers))
        with tracer.start_as_current_span("careermate.native", context=parent, attributes={
                "langfuse.observation.type": "agent", "keel.agent": agent.name,
                "keel.env": os.environ["KEEL_ENV"], "keel.status": "ok"}) as span:
            tid = f"{span.get_span_context().trace_id:032x}" if span.get_span_context().is_valid else uuid.uuid4().hex
            with use_transport(getattr(request.app.state, "keel_transport", None)):
                ctx = Context(agent.name, uuid.uuid4().hex, tid, asyncio.Queue(), tracer,
                              manifest=agent.manifest, env=os.environ["KEEL_ENV"])
                try:
                    yield ctx
                except BaseException:
                    span.set_attribute("keel.status", "failed")
                    raise
                finally:
                    await ctx.aclose()

    async def generate(ctx, body, *, streaming=False):
        messages = body["messages"]
        if not isinstance(messages, list) or not messages:
            raise ValueError("messages are required")
        for message in messages:
            if message.get("role") == "user" and isinstance(message.get("content"), str):
                ctx.guard.inspect("user", message["content"])
        prompt = await ctx.prompt(body["promptName"]) if body.get("promptName") else None
        if prompt and body.get("promptVersion") and prompt.version != body["promptVersion"]:
            raise KeelError(ErrorCode.PROMPT_UNAVAILABLE)
        if prompt and isinstance(prompt.body, str):
            messages = [TrustedMessage(message) if message.get("role") == "system"
                        and prompt.body in message.get("content", "") else message for message in messages]
        if body.get("temperature") is not None:
            temperature = body["temperature"]
            if type(temperature) not in (int, float) or not 0 <= temperature <= 2:
                raise ValueError("Invalid temperature")
            prompt = copy.copy(prompt) if prompt else SimpleNamespace(version=None, fallback=False)
            prompt.config = {**getattr(prompt, "config", {}), "temperature": temperature}
        result = await ctx.llm.chat(messages, model=body.get("model") or None,
                                   tools=body.get("tools") or None, prompt=prompt,
                                   emit_tokens=streaming, max_tokens=body.get("maxTokens"))
        calls = []
        if isinstance(result, ChatResult):
            calls = [{"id": c.id, "type": "function", "function": {
                "name": c.name, "arguments": json.dumps(c.arguments, ensure_ascii=False)}} for c in result.tool_calls]
            result = result.content
        if streaming:
            tail = ctx.guard.final(result)
            if tail:
                ctx._events.put_nowait(TokenEvent(text=tail))
        return {"content": result, "model": body.get("model") or ctx.llm.default_model,
                "provider": "keel", "toolCalls": calls, "traceId": ctx.trace_id}

    async def stream(request, body):
        async with context(request) as ctx:
            task = asyncio.create_task(generate(ctx, body, streaming=True))
            try:
                while not task.done() or not ctx._events.empty():
                    try:
                        event = await asyncio.wait_for(ctx._events.get(), timeout=0.1)
                    except asyncio.TimeoutError:
                        continue
                    if isinstance(event, TokenEvent):
                        yield json.dumps({"token": event.text}, ensure_ascii=False) + "\n"
                yield json.dumps({"final": await task}, ensure_ascii=False) + "\n"
            except Exception:
                trace.get_current_span().set_attribute("keel.status", "failed")
                yield json.dumps({"error": ErrorCode.SERVER_INTERNAL_ERROR.value}) + "\n"
            finally:
                if not task.done():
                    task.cancel()
                await asyncio.gather(task, return_exceptions=True)

    async def handle(request):
        try:
            body = await request.json()
            if not isinstance(body, dict):
                raise ValueError("Expected an object")
            if request.path_params["operation"] == "llm" and body.get("stream"):
                return StreamingResponse(stream(request, body), media_type="application/x-ndjson")
            async with context(request) as ctx:
                operation = request.path_params["operation"]
                if operation == "prompt":
                    prompt = await ctx.prompt(body["name"])
                    return JSONResponse({"name": prompt.name, "version": prompt.version,
                                         "content": prompt.body, "fallback": prompt.fallback})
                if operation == "audit":
                    # Every native tool is acknowledged synchronously before business execution.
                    name = body["toolName"]
                    declared = next((t for t in agent.manifest.spec.tools if t.name == name), None)
                    if declared is None:
                        raise KeelError(ErrorCode.TOOL_NOT_GRANTED)
                    await ctx.audit.record("tool.call", "high", "allowed", resource=name,
                                           payload={"toolName": name, "outcome": "requested"},
                                           trace_id=ctx.trace_id, run_id=ctx.run_id)
                    return JSONResponse({"accepted": True})
                if operation != "llm":
                    raise ValueError("Unknown operation")
                return JSONResponse(await generate(ctx, body))
        except KeelError as exc:
            return JSONResponse({"code": exc.code.value, "message": str(exc),
                                 "trace_id": "", "retryable": exc.code.retryable}, status_code=exc.code.http)
        except (ValueError, KeyError, TypeError):
            code = ErrorCode.SERVER_INVALID_PARAM
            return JSONResponse({"code": code.value, "message": code.message,
                                 "trace_id": "", "retryable": False}, status_code=400)

    app.router.routes.append(Route("/internal/{operation}", handle, methods=["POST"]))
