package com.careermate.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class McpJsonRpcResponse {

    private final String jsonrpc;
    private final JsonNode id;
    private final JsonNode result;
    private final McpJsonRpcError error;

    public static McpJsonRpcResponse success(JsonNode id, JsonNode result) {
        return McpJsonRpcResponse.builder()
                .jsonrpc(McpConstants.JSONRPC_VERSION)
                .id(id)
                .result(result)
                .build();
    }

    public static McpJsonRpcResponse failure(JsonNode id, int code, String message) {
        return McpJsonRpcResponse.builder()
                .jsonrpc(McpConstants.JSONRPC_VERSION)
                .id(id)
                .error(McpJsonRpcError.builder().code(code).message(message).build())
                .build();
    }
}
