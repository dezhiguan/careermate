package com.careermate.agent.multiagent;

public record SpecialistResult(
    AgentDomain domain,
    String toolName,        // 执行的工具名，null 表示没有执行工具
    String toolSummary,     // 工具执行摘要，注入 system prompt
    boolean success
) {
    public static SpecialistResult noTool(AgentDomain domain) {
        return new SpecialistResult(domain, null, null, true);
    }

    public static SpecialistResult withTool(AgentDomain domain, String toolName, String summary) {
        return new SpecialistResult(domain, toolName, summary, true);
    }

    public static SpecialistResult failed(AgentDomain domain, String reason) {
        return new SpecialistResult(domain, null, reason, false);
    }
}
