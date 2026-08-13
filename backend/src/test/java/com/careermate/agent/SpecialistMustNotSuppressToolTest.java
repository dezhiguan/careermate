package com.careermate.agent;

import com.careermate.agent.multiagent.SpecialistResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 专家 Agent 产出可用结果时会抑制工具执行——这是「说做了其实没做」的真正来源。
 *
 * <p>线上实测：说「帮我创建一次模拟面试训练」，面试专家写了一段关于面试题的文字，
 * shouldRunLegacyToolFallback 因此返回 false，工具路由整个被跳过，训练一条没建，
 * 模型却回复「已成功创建模拟面试训练（5题，首题已就绪）」；问薪资同理，市场专家写一段
 * 分析，真实行情接口从未被查，模型自己编了一组分位数还声称「我已调用薪资工具」。
 *
 * <p>本用例锁住这个前提：只要专家有可用结果，该 gate 就会关掉工具执行。所以调用方
 * （AgentKernelService / AgentStreamService）必须在确定性关键词命中时绕过它——
 * 专家产的是文本，不落库也不查接口，不能替代动作。
 */
class SpecialistMustNotSuppressToolTest {

    @Test
    void 专家有可用结果时该gate会关掉工具执行() {
        SpecialistResult usable = SpecialistResult.withTool(
                com.careermate.agent.multiagent.AgentDomain.INTERVIEW,
                "interview_specialist",
                "这里是一段关于面试题的文字，但没有创建任何训练");

        assertFalse(
                AgentPromptAssembler.shouldRunLegacyToolFallback(false, List.of(usable)),
                "确认前提成立：专家有可用结果 → 工具执行被抑制，调用方必须自行绕过");
    }
}
