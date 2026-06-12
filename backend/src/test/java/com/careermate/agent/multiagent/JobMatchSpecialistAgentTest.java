package com.careermate.agent.multiagent;

import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.AgentToolExecutionService;
import com.careermate.agent.tool.AgentToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobMatchSpecialistAgentTest {

    @Mock
    private AgentToolExecutionService toolExecutionService;

    private JobMatchSpecialistAgent agent;
    private AgentToolContext context;

    @BeforeEach
    void setUp() {
        agent = new JobMatchSpecialistAgent(toolExecutionService);
        context = AgentToolContext.builder().userId(1L).sessionId("S-1").build();
    }

    @Test
    void longJdUsesCreateJobMatch() {
        String jd = "岗位：Java 后端\n公司：腾讯\n要求：" + "Spring Boot ".repeat(30);
        when(toolExecutionService.execute(context, "create_job_match"))
                .thenReturn(success("已创建匹配"));

        SpecialistResult result = agent.process(context, jd);

        assertEquals("create_job_match", result.toolName());
    }

    @Test
    void shortMessageUsesLatestJobMatch() {
        when(toolExecutionService.execute(context, "get_latest_job_match"))
                .thenReturn(success("最近匹配 75 分"));

        SpecialistResult result = agent.process(context, "看看岗位匹配");

        assertEquals("get_latest_job_match", result.toolName());
    }

    @Test
    void failedExecutionReturnsNoTool() {
        when(toolExecutionService.execute(context, "get_latest_job_match"))
                .thenReturn(AgentToolResult.builder()
                        .toolName("get_latest_job_match")
                        .success(false)
                        .summary("无记录")
                        .build());

        SpecialistResult result = agent.process(context, "看看岗位匹配");

        assertNull(result.toolName());
        assertTrue(result.success());
    }

    @Test
    void exceptionReturnsFailedResult() {
        when(toolExecutionService.execute(eq(context), eq("get_latest_job_match")))
                .thenThrow(new RuntimeException("timeout"));

        SpecialistResult result = agent.process(context, "看看岗位匹配");

        assertFalse(result.success());
    }

    private static AgentToolResult success(String summary) {
        return AgentToolResult.builder()
                .toolName("get_latest_job_match")
                .success(true)
                .summary(summary)
                .build();
    }
}
