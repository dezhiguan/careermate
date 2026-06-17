package com.careermate.mcp;

import com.fasterxml.jackson.databind.JsonNode;

final class McpArgumentSupport {

    private McpArgumentSupport() {
    }

    static void rejectClientUserId(JsonNode arguments) {
        if (arguments == null || !arguments.isObject()) {
            return;
        }
        if (arguments.has("userId") || arguments.has("user_id")) {
            throw new McpInvalidParamsException("userId is not allowed in MCP arguments");
        }
    }

    static String requiredText(JsonNode arguments, String field, int maxLength) {
        if (arguments == null || !arguments.has(field) || arguments.get(field).isNull()) {
            throw new McpInvalidParamsException("Missing required parameter: " + field);
        }
        JsonNode node = arguments.get(field);
        if (!node.isTextual()) {
            throw new McpInvalidParamsException(field + " must be a string");
        }
        String value = node.asText("").trim();
        if (value.isEmpty()) {
            throw new McpInvalidParamsException("Missing required parameter: " + field);
        }
        if (value.length() > maxLength) {
            throw new McpInvalidParamsException(field + " exceeds max length " + maxLength);
        }
        return value;
    }

    static String optionalText(JsonNode arguments, String field, int maxLength) {
        if (arguments == null || !arguments.has(field) || arguments.get(field).isNull()) {
            return null;
        }
        JsonNode node = arguments.get(field);
        if (!node.isTextual()) {
            throw new McpInvalidParamsException(field + " must be a string");
        }
        String value = node.asText("").trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new McpInvalidParamsException(field + " exceeds max length " + maxLength);
        }
        return value;
    }

    static int optionalTopK(JsonNode arguments) {
        if (arguments == null || !arguments.has("topK") || arguments.get("topK").isNull()) {
            return McpConstants.DEFAULT_TOP_K;
        }
        JsonNode node = arguments.get("topK");
        if (!node.isInt()) {
            throw new McpInvalidParamsException("topK must be an integer");
        }
        int topK = node.asInt();
        if (topK < 1 || topK > McpConstants.MAX_TOP_K) {
            throw new McpInvalidParamsException("topK must be between 1 and " + McpConstants.MAX_TOP_K);
        }
        return topK;
    }

    static boolean optionalBoolean(JsonNode arguments, String field, boolean defaultValue) {
        if (arguments == null || !arguments.has(field) || arguments.get(field).isNull()) {
            return defaultValue;
        }
        JsonNode node = arguments.get(field);
        if (!node.isBoolean()) {
            throw new McpInvalidParamsException(field + " must be a boolean");
        }
        return node.asBoolean();
    }

    static Long optionalLong(JsonNode arguments, String field) {
        if (arguments == null || !arguments.has(field) || arguments.get(field).isNull()) {
            return null;
        }
        JsonNode node = arguments.get(field);
        if (!node.isIntegralNumber()) {
            throw new McpInvalidParamsException(field + " must be an integer");
        }
        return node.asLong();
    }
}
