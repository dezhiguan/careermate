package com.careermate.agent.tool;

import java.util.List;

final class AgentToolDefinitionSupport {

    private AgentToolDefinitionSupport() {
    }

    static AgentToolParameter stringParam(String name, boolean required, String description) {
        return AgentToolParameter.builder()
                .name(name)
                .type(AgentToolParameterType.STRING)
                .required(required)
                .description(description)
                .build();
    }

    static AgentToolParameter stringOrNumberParam(String name, boolean required, String description) {
        return AgentToolParameter.builder()
                .name(name)
                .type(AgentToolParameterType.STRING)
                .required(required)
                .description(description + "（可为字符串或数字）")
                .build();
    }

    static List<AgentToolParameter> noParameters() {
        return List.of();
    }
}
