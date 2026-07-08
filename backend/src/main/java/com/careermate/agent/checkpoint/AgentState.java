package com.careermate.agent.checkpoint;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;

/**
 * B3：可快照的 Agent 运行状态（裁剪版）。data 存 plan/observations 等自由字段；pendingAction 用于 HITL 暂停。
 * 不塞 LLM raw response（只留 summary），单个 state 建议 < 256KB。
 */
public record AgentState(
        String runId,
        int stepIndex,
        String currentStep,
        Map<String, Object> data,
        String pendingAction,
        boolean paused
) {
    public AgentState {
        if (data == null) {
            data = Map.of();
        }
    }

    public static AgentState initial(String runId) {
        return new AgentState(runId, 0, "created", Map.of(), null, false);
    }

    public AgentState advance(String stepName, Map<String, Object> newData) {
        return new AgentState(runId, stepIndex + 1, stepName, newData, pendingAction, false);
    }

    public AgentState pause(String action) {
        return new AgentState(runId, stepIndex, currentStep, data, action, true);
    }

    @JsonIgnore
    public boolean isTerminal() {
        return "done".equals(currentStep) || "failed".equals(currentStep);
    }
}
