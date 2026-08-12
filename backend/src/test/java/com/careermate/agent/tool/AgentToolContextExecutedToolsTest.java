package com.careermate.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 意图路由已执行的工具不能再挂给 function-calling，否则同一个动作会被执行两次。
 *
 * <p>线上实测：一句「帮我创建任务」落库两条完全相同的任务（相隔 7 秒），
 * 而 SSE 只发了一次 tool 事件、trace 也只有一条——第二次执行完全不留痕。
 */
class AgentToolContextExecutedToolsTest {

    @Test
    void 记录本轮已执行工具() {
        AgentToolContext ctx = AgentToolContext.builder()
                .userId(1L)
                .executedToolNames(Set.of("create_career_task"))
                .build();

        assertTrue(ctx.alreadyExecuted("create_career_task"));
        assertFalse(ctx.alreadyExecuted("get_salary_guidance"), "没执行过的工具仍要可用");
        assertFalse(ctx.alreadyExecuted(null));
    }

    @Test
    void 默认不排除任何工具() {
        AgentToolContext ctx = AgentToolContext.builder().userId(1L).build();
        assertFalse(ctx.alreadyExecuted("create_career_task"));
    }
}
