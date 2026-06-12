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
class InterviewSpecialistAgentTest {

    @Mock
    private AgentToolExecutionService toolExecutionService;

    private InterviewSpecialistAgent agent;
    private AgentToolContext context;

    @BeforeEach
    void setUp() {
        agent = new InterviewSpecialistAgent(toolExecutionService);
        context = AgentToolContext.builder().userId(1L).sessionId("S-1").build();
    }

    @Test
    void createsInterviewSessionOnSuccess() {
        when(toolExecutionService.execute(context, "create_interview_session"))
                .thenReturn(AgentToolResult.builder()
                        .toolName("create_interview_session")
                        .success(true)
                        .summary("已创建 5 题")
                        .build());

        SpecialistResult result = agent.process(context, "开始面试练习");

        assertEquals("create_interview_session", result.toolName());
        assertTrue(result.toolSummary().contains("已创建"));
    }

    @Test
    void failedExecutionReturnsNoTool() {
        when(toolExecutionService.execute(context, "create_interview_session"))
                .thenReturn(AgentToolResult.builder()
                        .toolName("create_interview_session")
                        .success(false)
                        .summary("失败")
                        .build());

        SpecialistResult result = agent.process(context, "开始面试练习");

        assertNull(result.toolName());
    }

    @Test
    void exceptionReturnsFailedResult() {
        when(toolExecutionService.execute(eq(context), eq("create_interview_session")))
                .thenThrow(new RuntimeException("boom"));

        SpecialistResult result = agent.process(context, "开始面试练习");

        assertFalse(result.success());
        assertEquals("boom", result.toolSummary());
    }
}
