package com.careermate.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class McpServer {

    private final McpResourceService mcpResourceService;
    private final McpToolService mcpToolService;
    private final ObjectMapper objectMapper;

    public McpServer(
            McpResourceService mcpResourceService,
            McpToolService mcpToolService,
            ObjectMapper objectMapper
    ) {
        this.mcpResourceService = mcpResourceService;
        this.mcpToolService = mcpToolService;
        this.objectMapper = objectMapper;
    }

    public McpJsonRpcResponse handle(Long userId, JsonNode request) {
        if (request == null || !request.isObject()) {
            return McpJsonRpcResponse.failure(null, McpConstants.ERROR_INVALID_REQUEST, "Invalid Request");
        }
        JsonNode id = request.get("id");
        String version = textOrNull(request.get("jsonrpc"));
        if (!McpConstants.JSONRPC_VERSION.equals(version)) {
            return McpJsonRpcResponse.failure(id, McpConstants.ERROR_INVALID_REQUEST, "Invalid Request");
        }

        String method = textOrNull(request.get("method"));
        if (method == null || method.isBlank()) {
            return McpJsonRpcResponse.failure(id, McpConstants.ERROR_INVALID_REQUEST, "method is required");
        }

        JsonNode params = request.get("params");
        try {
            JsonNode result = dispatch(userId, method, params);
            return McpJsonRpcResponse.success(id, result);
        } catch (MethodNotFoundException e) {
            return McpJsonRpcResponse.failure(id, McpConstants.ERROR_METHOD_NOT_FOUND, "Method not found");
        } catch (McpInvalidParamsException e) {
            return McpJsonRpcResponse.failure(id, McpConstants.ERROR_INVALID_PARAMS, e.getMessage());
        } catch (McpForbiddenException e) {
            return McpJsonRpcResponse.failure(id, McpConstants.ERROR_FORBIDDEN, "forbidden");
        } catch (Exception e) {
            return McpJsonRpcResponse.failure(id, McpConstants.ERROR_INTERNAL, "Internal error");
        }
    }

    private JsonNode dispatch(Long userId, String method, JsonNode params) {
        return switch (method) {
            case "initialize" -> initialize();
            case "tools/list" -> mcpToolService.listTools();
            case "tools/call" -> callTool(userId, params);
            case "resources/list" -> mcpResourceService.listResources();
            case "resources/read" -> readResource(userId, params);
            default -> throw new MethodNotFoundException();
        };
    }

    private JsonNode initialize() {
        var root = objectMapper.createObjectNode();
        root.put("protocolVersion", McpConstants.PROTOCOL_VERSION);
        var capabilities = root.putObject("capabilities");
        capabilities.putObject("tools").put("listChanged", false);
        var resources = capabilities.putObject("resources");
        resources.put("subscribe", false);
        resources.put("listChanged", false);
        var serverInfo = root.putObject("serverInfo");
        serverInfo.put("name", McpConstants.SERVER_NAME);
        serverInfo.put("version", McpConstants.SERVER_VERSION);
        return root;
    }

    private JsonNode callTool(Long userId, JsonNode params) {
        if (params == null || !params.isObject()) {
            throw new McpInvalidParamsException("params.name is required");
        }
        String name = textOrNull(params.get("name"));
        if (name == null || name.isBlank()) {
            throw new McpInvalidParamsException("params.name is required");
        }
        JsonNode arguments = params.get("arguments");
        if (arguments == null || arguments.isNull()) {
            arguments = objectMapper.createObjectNode();
        }
        return mcpToolService.callTool(userId, name, arguments);
    }

    private JsonNode readResource(Long userId, JsonNode params) {
        if (params == null || !params.isObject()) {
            throw new McpInvalidParamsException("params.uri is required");
        }
        String uri = textOrNull(params.get("uri"));
        if (uri == null || uri.isBlank()) {
            throw new McpInvalidParamsException("params.uri is required");
        }
        return mcpResourceService.readResource(userId, uri);
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText(null);
    }

    private static final class MethodNotFoundException extends RuntimeException {
    }
}
