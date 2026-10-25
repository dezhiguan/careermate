import asyncio
import json
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from starlette.testclient import TestClient
from starlette.responses import JSONResponse
from starlette.applications import Starlette
from starlette.routing import Route

import app
from runtime.security import ServiceBoundary
from tools.backend import CATALOG, invocation, register_tools
from keel.protocol.errors import KeelError


def test_manifest_and_callbacks_cover_all_declared_tools():
    assert len(CATALOG) == 21
    assert {t.name for t in app.agent.manifest.spec.tools} == set(app.agent._tools)
    assert len(app.agent.manifest.spec.prompts.items) == 5


@pytest.mark.parametrize("path", ["/v1/invoke", "/v1/feedback", "/v1/runs/arbitrary", "/internal/llm", "/internal/prompt", "/internal/audit"])
def test_service_boundary_denies_missing_or_wrong_credentials(path):
    with TestClient(app.app) as client:
        response = client.post(path, json={})
        assert response.status_code == 401
        response = client.post(path, json={}, headers={"X-Keel-Bridge": "wrong", "Authorization": "Bearer wrong"})
        assert response.status_code == 401


def test_cross_service_credentials_are_not_interchangeable():
    with TestClient(app.app) as client:
        assert client.post("/internal/llm", json={}, headers={"Authorization": "Bearer unit-bridge"}).status_code == 401
        assert client.post("/v1/invoke", json={}, headers={"X-Keel-Bridge": "unit-service"}).status_code == 401


def test_malformed_credential_bytes_are_rejected_as_unauthenticated():
    with TestClient(app.app) as client:
        response = client.post("/v1/invoke", json={}, headers=[(b"x-keel-bridge", b"\xff")])
        assert response.status_code == 401


def test_deployment_identity_and_environment_override_untrusted_headers():
    async def inspect(request):
        return JSONResponse({"agent": request.headers["x-keel-agent"], "env": request.headers["x-keel-env"]})
    client = TestClient(ServiceBoundary(Starlette(routes=[Route("/v1/invoke", inspect, methods=["POST"])]), app.agent.name))
    response = client.post("/v1/invoke", headers={"X-Keel-Bridge": "unit-bridge", "X-Keel-Agent": "victim", "X-Keel-Env": "prod"})
    assert response.json() == {"agent": "careermate-keel", "env": "staging"}


def test_health_and_manifest_are_discoverable_without_credentials():
    with TestClient(app.app) as client:
        assert client.get("/v1/health").status_code == 200
        manifest = client.get("/v1/manifest").json()
        assert manifest["manifest"]["metadata"]["name"] == "careermate-keel"


def test_evaluation_never_discovers_or_executes_personal_tools(monkeypatch):
    async def run():
        discover = AsyncMock(side_effect=AssertionError("personal tool discovery during evaluation"))
        monkeypatch.setattr(app, "definitions", discover)
        prompt = SimpleNamespace(compile=lambda: "system")
        ctx = SimpleNamespace(eval_run_id="eval", prompt=AsyncMock(return_value=prompt), loop=AsyncMock(return_value="final"))
        assert await app.career(SimpleNamespace(input={"text": "review"}), ctx) == "final"
        assert ctx.loop.call_args.args[1] == []
        assert ctx.loop.call_args.kwargs["prompt"] is prompt
        discover.assert_not_called()
    asyncio.run(run())


def test_tool_execution_uses_authenticated_context_not_model_identity(monkeypatch):
    async def run():
        remote = AsyncMock(return_value={"success": True})
        monkeypatch.setattr("tools.backend.backend", remote)
        ctx = SimpleNamespace(eval_run_id=None, user_id="42", session_id="session", run_id="run")
        token = invocation.set((ctx, "my request"))
        try:
            await app.agent._tools["create_career_task"](title="prepare")
            body = remote.call_args.kwargs["body"]
            assert body["authUserId"] == "42"
            assert body["arguments"] == {"title": "prepare"}
            assert body["sessionId"] == "session"
        finally:
            invocation.reset(token)
    asyncio.run(run())


@pytest.mark.parametrize("user_id", [None, "", "dev", "not-numeric"])
def test_missing_user_never_falls_back_to_another_account(monkeypatch, user_id):
    async def run():
        remote = AsyncMock(side_effect=AssertionError("must not read another account"))
        monkeypatch.setattr("tools.backend.backend", remote)
        token = invocation.set((SimpleNamespace(eval_run_id=None, user_id=user_id), "request"))
        try:
            assert not (await app.agent._tools["get_default_resume"]())["success"]
            remote.assert_not_called()
        finally:
            invocation.reset(token)
    asyncio.run(run())


def test_eval_guard_rejects_direct_tool_callback():
    async def run():
        token = invocation.set((SimpleNamespace(eval_run_id="eval"), "request"))
        try:
            with pytest.raises(KeelError):
                await app.agent._tools["create_career_task"](title="forbidden")
        finally:
            invocation.reset(token)
    asyncio.run(run())


def test_seed_cases_have_independent_expectations():
    from evals.scorers import career_quality
    rows = [json.loads(line) for line in (Path(__file__).resolve().parents[1] / "evals/seed.jsonl").read_text().splitlines()]
    assert len(rows) >= 10
    assert all(career_quality(row["expectedOutput"], "", row["tags"]) == 0 for row in rows)
    assert career_quality({"anyOf": [["Redis"]]}, "这是一个没有证据的随便回答。", []) == 0
