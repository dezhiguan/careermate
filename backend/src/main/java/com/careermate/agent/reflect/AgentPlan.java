package com.careermate.agent.reflect;

import java.util.List;

/**
 * A3：结构化 plan。
 *
 * @param planId          落库后的主键（未落库为 null）
 * @param runId           反思运行 id
 * @param roundNo         轮次
 * @param goals           目标
 * @param subgoals        子目标
 * @param successCriteria 成功标准（知识库锚定）
 * @param revisedFrom     修订自哪个 planId（首轮为 null）
 */
public record AgentPlan(
        Long planId,
        String runId,
        int roundNo,
        List<String> goals,
        List<String> subgoals,
        List<String> successCriteria,
        Long revisedFrom
) {
    public AgentPlan withPlanId(Long id) {
        return new AgentPlan(id, runId, roundNo, goals, subgoals, successCriteria, revisedFrom);
    }
}
