"""Exercise the real SDK SSE loop against isolated HTTP dependencies."""
import json
import threading
import pytest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from starlette.testclient import TestClient
import app


@pytest.mark.parametrize("mode", ["agent", "native-json", "native-stream"])
def test_authenticated_model_tool_audit_and_final_are_one_flow(monkeypatch, tmp_path, mode):
    state = {"model": 0, "audit": [], "business": []}

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def reply(self, data):
            encoded = json.dumps(data, ensure_ascii=False).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(encoded)))
            self.end_headers()
            self.wfile.write(encoded)

        def do_GET(self):
            if "/prompts/" in self.path:
                self.reply({"name": "careermate/assistant", "version": 3,
                            "type": "text", "prompt": "You are CareerMate", "config": {}})
            elif self.path == "/internal/keel/tools":
                self.reply([{"type": "function", "function": {"name": "get_default_resume",
                            "description": "Read the authenticated user's resume",
                            "parameters": {"type": "object", "properties": {}}}}])
            else:
                self.send_error(404)

        def do_POST(self):
            body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            if self.path == "/api/v1/audit/events":
                state["audit"].append(body)
                return self.reply({"accepted": True})
            if self.path.startswith("/internal/keel/tools/"):
                assert self.headers["Authorization"] == "Bearer unit-service"
                state["business"].append(body)
                return self.reply({"success": True, "summary": "用户尚未设置默认简历", "data": {"available": False}})
            if self.path == "/v1/chat/completions":
                state["model"] += 1
                if not body.get("stream"):
                    return self.reply({"id": "test", "object": "chat.completion", "created": 1, "model": "chat-default",
                        "choices": [{"index": 0, "message": {"role": "assistant", "content": "这是原生模型入口的真实 SDK 回复。"}, "finish_reason": "stop"}],
                        "usage": {"prompt_tokens": 12, "completion_tokens": 8, "total_tokens": 20}})
                if mode != "agent":
                    delta = {"content": "这是原生流式入口的真实 SDK 回复。"}
                elif state["model"] == 1:
                    delta = {"tool_calls": [{"index": 0, "id": "call_1", "type": "function", "function": {
                        "name": "get_default_resume", "arguments": "{}"}}]}
                else:
                    assert any(m["role"] == "tool" for m in body["messages"])
                    delta = {"content": "你尚未设置默认简历，请先上传真实简历，我会帮你评审。"}
                self.send_response(200)
                self.send_header("Content-Type", "text/event-stream")
                self.send_header("X-Request-Id", "test-gateway-receipt")
                self.end_headers()
                chunks = [
                    {"id": "test", "object": "chat.completion.chunk", "created": 1, "model": "chat-default",
                     "choices": [{"index": 0, "delta": delta, "finish_reason": None}]},
                    {"id": "test", "object": "chat.completion.chunk", "created": 1, "model": "chat-default",
                     "choices": [], "usage": {"prompt_tokens": 12, "completion_tokens": 8, "total_tokens": 20}},
                ]
                for chunk in chunks:
                    self.wfile.write(("data: " + json.dumps(chunk, ensure_ascii=False) + "\n\n").encode())
                self.wfile.write(b"data: [DONE]\n\n")
                return
            self.send_error(404)

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    base = f"http://127.0.0.1:{server.server_port}"
    for key in ("KEEL_LLM_BASE_URL", "KEEL_AUDIT_URL", "CAREERMATE_BACKEND_URL", "LANGFUSE_HOST"):
        monkeypatch.setenv(key, base)
    monkeypatch.setenv("LANGFUSE_PUBLIC_KEY", "test-public")
    monkeypatch.setenv("LANGFUSE_SECRET_KEY", "test-secret")
    monkeypatch.setenv("KEEL_AUDIT_SPOOL_PATH", str(tmp_path / "audit.jsonl"))
    try:
        with TestClient(app.app) as client:
            if mode == "agent":
                response = client.post("/v1/invoke", headers={"X-Keel-Bridge": "unit-bridge"},
                                       json={"input": {"text": "帮我看一下默认简历"}, "context": {"user_id": "42"}})
            else:
                response = client.post("/internal/llm", headers={"Authorization": "Bearer unit-service"}, json={
                    "messages": [{"role": "user", "content": "native request"}], "stream": mode == "native-stream"})
        assert response.status_code == 200
        if mode != "agent":
            assert state["model"] == 1
            assert state["business"] == []
            if mode == "native-stream":
                frames = [json.loads(line) for line in response.text.splitlines()]
                assert frames[0]["token"] == "这是原生流式入口的真实 SDK 回复。"
                assert frames[-1]["final"]["provider"] == "keel"
            else:
                assert response.json()["provider"] == "keel"
                assert response.json()["content"] == "这是原生模型入口的真实 SDK 回复。"
            return
        assert "event: final" in response.text, response.text
        assert "尚未设置默认简历" in response.text
        assert state["model"] == 2
        assert len(state["business"]) == 1
        assert state["business"][0]["authUserId"] == "42"
        assert state["business"][0]["arguments"] == {}
        assert len(state["audit"]) == 1
        assert state["audit"][0]["payload"] == {}
        assert state["audit"][0]["resource"] == "get_default_resume"
        final = json.loads(response.text.split("event: final\n", 1)[1].split("data:", 1)[1].strip())
        assert final["meta"]["prompt_versions"]["assistant"] == 3
        assert not final["meta"]["prompt_fallback"]
    finally:
        server.shutdown()
