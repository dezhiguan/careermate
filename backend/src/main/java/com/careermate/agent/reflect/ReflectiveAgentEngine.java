package com.careermate.agent.reflect;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Function;

/**
 * A3：Plan-Act-Reflect-Revise 反思闭环（Reflexion 裁剪版）。
 *
 * <p>plan → act → reflect →(不满意)→ revise → 回到 act。硬上限 {@code maxRounds}，
 * 连续 2 轮相同建议早停，verdict=FAIL 立即停。仅 deep-path 调用。
 */
@Slf4j
@Service
public class ReflectiveAgentEngine {

    private final AgentPlanner planner;
    private final AgentReflector reflector;
    private final AgentRepairer repairer;
    private final ReflectionProperties properties;

    public ReflectiveAgentEngine(AgentPlanner planner, AgentReflector reflector,
                                 AgentRepairer repairer, ReflectionProperties properties) {
        this.planner = planner;
        this.reflector = reflector;
        this.repairer = repairer;
        this.properties = properties;
    }

    /**
     * 运行反思闭环。
     *
     * @param runId       运行 id（用于串联 plan/reflection）
     * @param userMessage 用户任务
     * @param act         执行函数：给定当前 plan，产出可供审视的执行结果摘要（草稿答案）
     */
    public ReflectiveRunResult run(String runId, String userMessage, Function<AgentPlan, String> act) {
        int maxRounds = Math.max(1, properties.getMaxRounds());
        AgentPlan plan = planner.plan(runId, userMessage);
        Reflection previous = null;
        Reflection last = null;

        for (int round = 0; round < maxRounds; round++) {
            String actSummary;
            try {
                actSummary = act.apply(plan);
            } catch (Exception e) {
                log.warn("反思闭环 act 执行失败，提前结束（不影响对话）: {}", e.getMessage());
                return new ReflectiveRunResult(plan, last, "FAIL", round + 1);
            }

            Reflection reflection = reflector.review(plan, actSummary);
            last = reflection;

            if (reflection.satisfied() || "ACCEPT".equalsIgnoreCase(reflection.verdict())) {
                return new ReflectiveRunResult(plan, reflection, "CONSENSUS", round + 1);
            }
            if ("FAIL".equalsIgnoreCase(reflection.verdict())) {
                return new ReflectiveRunResult(plan, reflection, "FAIL", round + 1);
            }
            if (repairer.isStuck(previous, reflection)) {
                return new ReflectiveRunResult(plan, reflection, "STUCK_EARLY_STOP", round + 1);
            }
            previous = reflection;
            plan = repairer.revise(plan, reflection);
        }
        return new ReflectiveRunResult(plan, last, "MAX_ROUNDS", maxRounds);
    }
}
