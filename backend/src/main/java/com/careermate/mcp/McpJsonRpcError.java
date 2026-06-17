package com.careermate.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class McpJsonRpcError {

    private final int code;
    private final String message;
    private final JsonNode data;
}
