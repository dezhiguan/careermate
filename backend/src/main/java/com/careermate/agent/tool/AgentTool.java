package com.careermate.agent.tool;

import java.util.List;

public interface AgentTool {

    String name();

    String description();

    default AgentToolDefinition definition() {
        return AgentToolDefinition.builder()
                .name(name())
                .displayName(name())
                .description(description())
                .domain(AgentToolDomain.GENERAL)
                .permission(AgentToolPermission.READ_USER_DATA)
                .riskLevel(AgentToolRiskLevel.LOW)
                .parameters(List.of())
                .enabled(true)
                .version("1")
                .build();
    }

    boolean supports(AgentToolContext context);

    AgentToolResult execute(AgentToolContext context);
}
