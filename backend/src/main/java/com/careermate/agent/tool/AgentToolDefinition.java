package com.careermate.agent.tool;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class AgentToolDefinition {

    String name;
    String displayName;
    String description;
    AgentToolDomain domain;
    AgentToolPermission permission;
    AgentToolRiskLevel riskLevel;
    @Singular("parameter")
    List<AgentToolParameter> parameters;
    @Builder.Default
    boolean enabled = true;
    @Builder.Default
    String version = "1";
    @Singular("example")
    List<String> examples;

    public static AgentToolDefinitionBuilder base(
            String name,
            String displayName,
            String description,
            AgentToolDomain domain,
            AgentToolPermission permission,
            AgentToolRiskLevel riskLevel
    ) {
        return builder()
                .name(name)
                .displayName(displayName)
                .description(description)
                .domain(domain)
                .permission(permission)
                .riskLevel(riskLevel)
                .enabled(true)
                .version("1");
    }
}
