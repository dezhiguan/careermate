package com.careermate.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpServerTest {

    @Mock
    private McpResourceService mcpResourceService;
    @Mock
    private McpToolService mcpToolService;

    private McpServer mcpServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mcpServer = new McpServer(mcpResourceService, mcpToolService, objectMapper);
    }

    @Test
    void initializeReturnsProtocolVersion() {
        ObjectNode request = baseRequest("initialize");

        McpJsonRpcResponse response = mcpServer.handle(1L, request);

        assertEquals(McpConstants.PROTOCOL_VERSION, response.getResult().get("protocolVersion").asText());
        assertEquals(McpConstants.SERVER_NAME, response.getResult().get("serverInfo").get("name").asText());
    }

    @Test
    void unknownMethodReturnsMethodNotFound() {
        ObjectNode request = baseRequest("ping");

        McpJsonRpcResponse response = mcpServer.handle(1L, request);

        assertEquals(McpConstants.ERROR_METHOD_NOT_FOUND, response.getError().getCode());
    }

    @Test
    void toolsListDelegatesToToolService() {
        ObjectNode tools = objectMapper.createObjectNode();
        tools.putArray("tools");
        when(mcpToolService.listTools()).thenReturn(tools);

        McpJsonRpcResponse response = mcpServer.handle(1L, baseRequest("tools/list"));

        assertTrue(response.getResult().has("tools"));
    }

    @Test
    void resourcesReadDelegatesWithUserId() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.putArray("contents");
        when(mcpResourceService.readResource(eq(9L), eq(McpConstants.URI_RESUME_VERSIONS))).thenReturn(payload);

        ObjectNode params = objectMapper.createObjectNode();
        params.put("uri", McpConstants.URI_RESUME_VERSIONS);
        ObjectNode request = baseRequest("resources/read");
        request.set("params", params);

        McpJsonRpcResponse response = mcpServer.handle(9L, request);

        assertTrue(response.getResult().has("contents"));
    }

    @Test
    void invalidParamsReturnInvalidParamsError() {
        ObjectNode request = baseRequest("tools/call");
        request.putObject("params");

        McpJsonRpcResponse response = mcpServer.handle(1L, request);

        assertEquals(McpConstants.ERROR_INVALID_PARAMS, response.getError().getCode());
    }

    @Test
    void toolsCallDelegatesToToolService() {
        ObjectNode toolResult = objectMapper.createObjectNode();
        toolResult.put("isError", false);
        when(mcpToolService.callTool(eq(1L), eq(McpConstants.TOOL_SEARCH_JD), any())).thenReturn(toolResult);

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", McpConstants.TOOL_SEARCH_JD);
        params.putObject("arguments").put("query", "Java");
        ObjectNode request = baseRequest("tools/call");
        request.set("params", params);

        McpJsonRpcResponse response = mcpServer.handle(1L, request);

        assertFalse(response.getResult().get("isError").asBoolean());
    }

    private ObjectNode baseRequest(String method) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", McpConstants.JSONRPC_VERSION);
        request.put("id", 1);
        request.put("method", method);
        return request;
    }
}
