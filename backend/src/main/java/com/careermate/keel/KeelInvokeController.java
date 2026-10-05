package com.careermate.keel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Keel console entry. Existing /api routes stay on the user session. */
@RestController
public class KeelInvokeController {
    private final KeelBridge bridge;
    private final ObjectMapper json;
    private final String token;

    public KeelInvokeController(KeelBridge bridge, ObjectMapper json,
                                @Value("${KEEL_CAREERMATE_TOKEN:}") String token) {
        this.bridge = bridge;
        this.json = json;
        this.token = token == null ? "" : token;
    }

    @GetMapping("/v1/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/v1/manifest")
    public Map<String, String> manifest() {
        return Map.of("name", "careermate", "version", "v1");
    }

    @PostMapping(value = "/v1/invoke", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<String> invoke(HttpServletRequest request) throws Exception {
        if (token.isBlank() || !token.equals(request.getHeader("X-Keel-Bridge"))) {
            return ResponseEntity.status(401).body(KeelReplies.errorEvent(
                    "RUN_RESUME_DENIED", "底座桥接凭据无效", traceId(request), runId()));
        }
        JsonNode body;
        try {
            body = json.readTree(request.getInputStream());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(KeelReplies.errorEvent(
                    "SERVER_INVALID_PARAM", "请求参数不合法", traceId(request), runId()));
        }
        var text = body.path("input").path("text").asText("");
        var traceId = traceId(request);
        var runId = runId();
        if (text.isBlank()) {
            return ResponseEntity.badRequest().body(KeelReplies.errorEvent(
                    "SERVER_INVALID_PARAM", "请求参数不合法", traceId, runId));
        }
        try {
            return ResponseEntity.ok(KeelReplies.finalEvent(bridge.answer(text), traceId, runId));
        } catch (RuntimeException e) {
            var message = e.getMessage() == null ? "CareerMate 调用失败" : e.getMessage();
            return ResponseEntity.status(503).body(KeelReplies.errorEvent("GW_AGENT_OFFLINE", message, traceId, runId));
        }
    }

    private static String traceId(HttpServletRequest request) {
        var header = request.getHeader("traceparent");
        if (header != null && header.length() >= 36) {
            var parts = header.split("-");
            if (parts.length >= 2 && parts[1].length() == 32) {
                return parts[1];
            }
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String runId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
