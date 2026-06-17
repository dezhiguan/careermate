package com.careermate.mcp;

import com.careermate.security.CurrentUserContext;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mcp")
public class McpController {

    private final McpProperties mcpProperties;
    private final McpServer mcpServer;

    public McpController(McpProperties mcpProperties, McpServer mcpServer) {
        this.mcpProperties = mcpProperties;
        this.mcpServer = mcpServer;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<McpJsonRpcResponse> handle(@RequestBody(required = false) JsonNode body) {
        JsonNode requestId = body != null ? body.get("id") : null;
        if (!mcpProperties.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(McpJsonRpcResponse.failure(requestId, McpConstants.ERROR_DISABLED, "MCP endpoint is disabled"));
        }

        Long userId = CurrentUserContext.getUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(McpJsonRpcResponse.failure(requestId, McpConstants.ERROR_UNAUTHORIZED, "Unauthorized"));
        }

        if (body == null || !body.isObject()) {
            return ResponseEntity.badRequest()
                    .body(McpJsonRpcResponse.failure(requestId, McpConstants.ERROR_PARSE, "Parse error"));
        }

        McpJsonRpcResponse response = mcpServer.handle(userId, body);
        HttpStatus status = resolveStatus(response);
        return ResponseEntity.status(status).body(response);
    }

    private HttpStatus resolveStatus(McpJsonRpcResponse response) {
        if (response.getError() == null) {
            return HttpStatus.OK;
        }
        return switch (response.getError().getCode()) {
            case McpConstants.ERROR_UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case McpConstants.ERROR_FORBIDDEN -> HttpStatus.FORBIDDEN;
            case McpConstants.ERROR_DISABLED -> HttpStatus.SERVICE_UNAVAILABLE;
            case McpConstants.ERROR_PARSE, McpConstants.ERROR_INVALID_REQUEST, McpConstants.ERROR_INVALID_PARAMS ->
                    HttpStatus.BAD_REQUEST;
            default -> HttpStatus.OK;
        };
    }
}
