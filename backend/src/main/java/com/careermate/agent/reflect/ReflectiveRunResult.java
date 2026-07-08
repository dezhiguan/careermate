package com.careermate.agent.reflect;

/**
 * A3：反思闭环运行结果。
 *
 * @param finalPlan      最终（可能被修订过的）plan
 * @param lastReflection 最后一轮 reflection（可能为 null）
 * @param status         CONSENSUS / STUCK_EARLY_STOP / MAX_ROUNDS / FAIL
 * @param rounds         实际执行轮数
 */
public record ReflectiveRunResult(
        AgentPlan finalPlan,
        Reflection lastReflection,
        String status,
        int rounds
) {
    public boolean reachedConsensus() {
        return "CONSENSUS".equals(status);
    }
}
