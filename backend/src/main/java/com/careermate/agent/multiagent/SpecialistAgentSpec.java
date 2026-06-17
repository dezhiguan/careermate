package com.careermate.agent.multiagent;

import java.util.Collections;
import java.util.List;

public record SpecialistAgentSpec(
        AgentDomain domain,
        String agentName,
        String systemInstruction,
        List<String> allowedTools,
        String inputSchemaName,
        String outputSchemaName
) {

    public static final String INPUT_SCHEMA_V1 = "SpecialistAgentInputV1";
    public static final String OUTPUT_SCHEMA_V1 = "SpecialistResultV1";

    public SpecialistAgentSpec {
        if (domain == null) {
            throw new IllegalArgumentException("domain is required");
        }
        if (agentName == null || agentName.isBlank()) {
            throw new IllegalArgumentException("agentName is required");
        }
        if (systemInstruction == null || systemInstruction.isBlank()) {
            throw new IllegalArgumentException("systemInstruction is required");
        }
        if (inputSchemaName == null || inputSchemaName.isBlank()) {
            throw new IllegalArgumentException("inputSchemaName is required");
        }
        if (outputSchemaName == null || outputSchemaName.isBlank()) {
            throw new IllegalArgumentException("outputSchemaName is required");
        }
        allowedTools = allowedTools == null
                ? List.of()
                : Collections.unmodifiableList(List.copyOf(allowedTools));
    }
}
