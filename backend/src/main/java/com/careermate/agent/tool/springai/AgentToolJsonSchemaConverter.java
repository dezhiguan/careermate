package com.careermate.agent.tool.springai;

import com.careermate.agent.tool.AgentToolDefinition;
import com.careermate.agent.tool.AgentToolParameter;
import com.careermate.agent.tool.AgentToolParameterType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

final class AgentToolJsonSchemaConverter {

    private AgentToolJsonSchemaConverter() {
    }

    static String toInputJsonSchema(AgentToolDefinition definition, ObjectMapper objectMapper) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        ArrayNode required = root.putArray("required");

        List<AgentToolParameter> parameters = definition.getParameters() == null
                ? List.of()
                : definition.getParameters();
        for (AgentToolParameter parameter : parameters) {
            ObjectNode property = properties.putObject(parameter.getName());
            property.put("type", toJsonType(parameter.getType()));
            if (parameter.getDescription() != null && !parameter.getDescription().isBlank()) {
                property.put("description", parameter.getDescription());
            }
            if (parameter.getDefaultValue() != null) {
                property.put("default", parameter.getDefaultValue());
            }
            if (parameter.getEnumValues() != null && !parameter.getEnumValues().isEmpty()) {
                ArrayNode enumValues = property.putArray("enum");
                parameter.getEnumValues().forEach(enumValues::add);
            }
            if (parameter.isRequired()) {
                required.add(parameter.getName());
            }
        }

        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to build tool input schema for " + definition.getName(), e);
        }
    }

    private static String toJsonType(AgentToolParameterType type) {
        return switch (type) {
            case STRING -> "string";
            case NUMBER -> "number";
            case BOOLEAN -> "boolean";
            case OBJECT -> "object";
            case ARRAY -> "array";
        };
    }
}
