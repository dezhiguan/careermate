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
}
