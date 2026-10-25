"""CareerMate orchestration runs on Keel; Java retains the business domain."""
import asyncio
import os
import socket
from pathlib import Path

from keel import Agent

from runtime.security import ServiceBoundary
from tools.backend import definitions, invocation, register_tools

for path_key in ("KEEL_AUDIT_SPOOL_PATH", "KEEL_TRACE_BUFFER_PATH"):
    if path_key in os.environ:
        os.environ[path_key] = os.environ[path_key].replace("{pod}", socket.gethostname())

agent = Agent.from_manifest(Path(__file__).with_name("agent.yaml"))
agent.manifest.spec.runtime.endpoint = os.environ["CAREERMATE_AGENT_ENDPOINT"]
register_tools(agent)


@agent.entry
async def career(request, ctx):
    # Freeze all native prompt dependencies into the same release evidence.
    # Java operations can use any of these templates after this agent is published.
    prompts = await asyncio.gather(*(ctx.prompt(item.name) for item in agent.manifest.spec.prompts.items))
    prompt = prompts[0]
    text = request.input["text"]
    tools = [] if ctx.eval_run_id else await definitions()
    token = invocation.set((ctx, text))
    try:
        return await ctx.loop([
            {"role": "system", "content": prompt.compile()},
            {"role": "user", "content": text},
        ], tools, prompt=prompt, max_tool_rounds=3)
    finally:
        invocation.reset(token)


protocol = agent.asgi()
from runtime.internal import install_internal
install_internal(protocol, agent)
app = ServiceBoundary(protocol, agent.name)
