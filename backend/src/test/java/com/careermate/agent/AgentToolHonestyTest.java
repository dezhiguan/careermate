package com.careermate.agent;

import com.careermate.agent.tool.AgentToolResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工具结果回灌提示词时必须让「失败」无法被忽略。
 *
 * <p>线上实测过：create_career_task 抛 DateTimeParseException 返回失败，小职照样回复
 * 「任务创建成功」，而任务表里什么都没有。根因是失败只体现在「结果摘要」这一句自然语言里，
 * 提示词既没有独立的状态字段，也没有任何禁止措辞的约束。
 */
class AgentToolHonestyTest {

    @Test
    void 失败的工具结果必须带出显式状态与禁止措辞约束() {
        AgentToolResult failed = AgentToolResult.failure(
                "create_career_task", "工具执行失败", "Text '周五' could not be parsed at index 0");

        String prompt = AgentPromptAssembler.appendToolResult("你是小职。", failed);

        assertTrue(prompt.contains("执行状态：失败"), "必须有独立的状态行，不能只藏在摘要里");
        assertTrue(prompt.contains("Text '周五' could not be parsed at index 0"), "必须带上原始错误");
        assertTrue(prompt.contains("动作并没有发生"), "必须点明动作未发生");
        assertTrue(prompt.contains("严禁"), "必须显式禁止成功措辞");
    }

    @Test
    void 成功的工具结果不带失败约束() {
        AgentToolResult ok = AgentToolResult.success(
                "create_career_task", "已创建任务：补完 K8s", java.util.Map.of("taskId", 7));

        String prompt = AgentPromptAssembler.appendToolResult("你是小职。", ok);

        assertTrue(prompt.contains("执行状态：成功"));
        assertFalse(prompt.contains("动作并没有发生"));
    }

    @Test
    void 复用已有记录时必须禁止新建措辞() {
        // 面试训练工具是幂等的：已有未完成的训练就返回它。此前结果里虽然带了 reused 标记，
        // 模型仍回「已成功创建面试训练（5题，当前第1题）」，用户点进去是昨天那场没答完的。
        AgentToolResult reused = AgentToolResult.success(
                "create_interview_session", "你已有一场未完成的面试训练",
                java.util.Map.of("reused", true, "sessionId", 51));

        String prompt = AgentPromptAssembler.appendToolResult("你是小职。", reused);

        assertTrue(prompt.contains("并没有新建"), "必须点明本次没有新建记录");
        assertTrue(prompt.contains("严禁"), "必须显式禁止「已创建」这类措辞");
    }

    @Test
    void 真正新建时不加复用约束() {
        AgentToolResult created = AgentToolResult.success(
                "create_interview_session", "已新建面试训练",
                java.util.Map.of("reused", false, "sessionId", 52));

        String prompt = AgentPromptAssembler.appendToolResult("你是小职。", created);

        assertFalse(prompt.contains("并没有新建"), "真新建时不该被误加约束");
    }
}
