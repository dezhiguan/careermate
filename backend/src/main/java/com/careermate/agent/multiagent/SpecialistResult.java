package com.careermate.agent.multiagent;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class SpecialistResult {

    private final AgentDomain domain;
    @Builder.Default
    private final String agentName = "";
    @Builder.Default
    private final String intent = "";
    private final String toolName;
    private final String summary;
    @Builder.Default
    private final Map<String, Object> structuredData = Collections.emptyMap();
    @Builder.Default
    private final List<String> citations = Collections.emptyList();
    @Builder.Default
    private final SpecialistResultStatus status = SpecialistResultStatus.SUCCESS;
    @Builder.Default
    private final SpecialistRiskLevel riskLevel = SpecialistRiskLevel.LOW;
    @Builder.Default
    private final List<String> warnings = Collections.emptyList();

    public AgentDomain domain() {
        return domain;
    }

    public String toolName() {
        return toolName;
    }

    public String toolSummary() {
        return summary;
    }

    public boolean success() {
        return status == SpecialistResultStatus.SUCCESS;
    }

    public static SpecialistResult noTool(AgentDomain domain) {
        return builder()
                .domain(domain)
                .agentName(defaultAgentName(domain))
                .status(SpecialistResultStatus.NO_ACTION)
                .riskLevel(SpecialistRiskLevel.LOW)
                .build();
    }

    public static SpecialistResult withTool(AgentDomain domain, String toolName, String summary) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("toolName", toolName);
        data.put("success", true);
        return builder()
                .domain(domain)
                .agentName(defaultAgentName(domain))
                .toolName(toolName)
                .summary(summary)
                .status(SpecialistResultStatus.SUCCESS)
                .riskLevel(SpecialistRiskLevel.LOW)
                .structuredData(data)
                .build();
    }

    public static SpecialistResult failed(AgentDomain domain, String reason) {
        return builder()
                .domain(domain)
                .agentName(defaultAgentName(domain))
                .summary(reason)
                .status(SpecialistResultStatus.FAILED)
                .riskLevel(SpecialistRiskLevel.MEDIUM)
                .build();
    }

    private static String defaultAgentName(AgentDomain domain) {
        return domain == null ? "SpecialistAgent" : domain.name();
    }
}
