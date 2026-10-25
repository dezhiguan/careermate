"""Authenticate the control plane and the Java service independently."""
import hmac
import os

from starlette.responses import JSONResponse
from keel.protocol.errors import ErrorCode


class ServiceBoundary:
    def __init__(self, app):
        self.app = app
        self.bridge = os.environ["KEEL_BRIDGE_TOKEN"]
        self.service = os.environ["CAREERMATE_SERVICE_TOKEN"]
        self.env = os.environ["KEEL_ENV"]
        if not self.bridge or not self.service or self.bridge == self.service:
            raise RuntimeError("Distinct, nonempty bridge and business service credentials are required")
        if self.env not in ("staging", "prod"):
            raise RuntimeError("KEEL_ENV must be staging or prod")

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            return await self.app(scope, receive, send)
        path = scope["path"]
        if path in ("/v1/health", "/v1/manifest"):
            return await self.app(scope, receive, send)
        headers = dict(scope.get("headers", []))
        internal = path.startswith("/internal/")
        supplied = headers.get(b"authorization", b"") if internal else headers.get(b"x-keel-bridge", b"")
        expected = ("Bearer " + self.service if internal else self.bridge).encode()
        if not hmac.compare_digest(supplied, expected):
            code = ErrorCode.AUTH_UNAUTHENTICATED
            return await JSONResponse({"code": code.value, "message": code.message,
                                       "trace_id": "", "retryable": False}, status_code=401)(scope, receive, send)
        # Agent and environment belong to the deployment, never to caller headers.
        scope = dict(scope)
        scope["headers"] = [(k, v) for k, v in scope["headers"] if k.lower() not in (b"x-keel-agent", b"x-keel-env")]
        scope["headers"] += [(b"x-keel-agent", b"careermate"), (b"x-keel-env", self.env.encode())]
        await self.app(scope, receive, send)
