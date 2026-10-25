"""The model supplies business arguments; identity stays outside its schema."""
import contextvars
import json
import os
from pathlib import Path

import httpx
from opentelemetry.trace.propagation.tracecontext import TraceContextTextMapPropagator
from keel.protocol.errors import ErrorCode, KeelError

invocation = contextvars.ContextVar("careermate_invocation")
CATALOG = json.loads(Path(__file__).with_name("catalog.json").read_text())


async def backend(path, *, body=None):
    headers = {"Authorization": "Bearer " + os.environ["CAREERMATE_SERVICE_TOKEN"]}
    TraceContextTextMapPropagator().inject(headers)
    async with httpx.AsyncClient(base_url=os.environ["CAREERMATE_BACKEND_URL"], timeout=90) as client:
        response = await client.request("GET" if body is None else "POST", path, headers=headers, json=body)
        if response.status_code == 403 and body is not None:
            return {"success": False, "summary": "当前底座账号尚未关联可用的 CareerMate 账户，请先登录 CareerMate。"}
        response.raise_for_status()
        return response.json()


def register_tools(agent):
    for declared in CATALOG:
        name = declared["name"]

        def make_callback(tool_name):
            async def call(**arguments):
                ctx, text = invocation.get()
                if ctx.eval_run_id:
                    raise KeelError(ErrorCode.TOOL_NOT_GRANTED)
                if not ctx.user_id or not str(ctx.user_id).isdecimal():
                    return {"success": False, "summary": "请先登录 CareerMate 建立业务账户"}
                result = await backend("/internal/keel/tools/" + tool_name, body={
                    "authUserId": ctx.user_id, "sessionId": ctx.session_id or ctx.run_id,
                    "userMessage": text, "arguments": arguments,
                })
                return result
            return call

        agent.tool(name=name)(make_callback(name))


async def definitions():
    declared = {row["name"] for row in CATALOG}
    rows = await backend("/internal/keel/tools")
    return [row for row in rows if row.get("function", {}).get("name") in declared]
