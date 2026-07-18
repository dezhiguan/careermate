package com.careermate.agent.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AgentToolExecutionServiceTest {

    @Mock
    private AgentToolRegistry registry;

    @Mock
    private AgentTool readOnlyTool;

    @Mock
    private AgentTool writeTool;

    private AgentToolExecutionService service;

    @BeforeEach
    void setUp() {
        service = new AgentToolExecutionService(registry, new AgentToolArgumentValidator());
    }

    @Test
    void unknownToolReturnsFailure() {
        when(registry.findByName("missing_tool")).thenReturn(Optional.empty());

        AgentToolResult result = service.execute(context(Map.of()), "missing_tool");

        assertFalse(result.isSuccess());
        assertEquals("未知工具", result.getSummary());
        verify(readOnlyTool, never()).execute(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void userIdInArgsReturnsFailure() {
        stubTool("create_career_task", new CreateCareerTaskTool(null).definition());

        AgentToolResult result = service.execute(
                context(Map.of("userId", 99L, "title", "完善简历")),
                "create_career_task"
        );

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("userId"));
        verify(writeTool, never()).execute(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createJobMatchMissingJdContentReturnsFailure() {
        stubTool("create_job_match", new CreateJobMatchTool(null).definition());

        AgentToolResult result = service.execute(
                AgentToolContext.builder()
                        .userId(1L)
                        .sessionId("S-1")
                        .userMessage("")
                        .args(Map.of())
                        .build(),
                "create_job_match"
        );

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("jdContent"));
    }

    @Test
    void createCareerTaskMissingTitleReturnsFailure() {
        stubTool("create_career_task", new CreateCareerTaskTool(null).definition());

        AgentToolResult result = service.execute(context(Map.of()), "create_career_task");

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("title"));
    }

    @Test
    void markCareerTaskDoneMissingIdentifiersReturnsFailure() {
        stubTool("mark_career_task_done", new MarkCareerTaskDoneTool(null).definition());

        AgentToolResult result = service.execute(context(Map.of()), "mark_career_task_done");

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("taskId"));
    }

    @Test
    void generateResumeFromJdMissingSessionIdReturnsFailure() {
        stubTool("generate_resume_from_jd", new GenerateResumeFromJdTool(null, null, null, null, null).definition());

        AgentToolResult result = service.execute(
                AgentToolContext.builder()
                        .userId(1L)
                        .sessionId(null)
                        .userMessage("生成简历")
                        .args(Map.of())
                        .build(),
                "generate_resume_from_jd"
        );

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("sessionId"));
    }

    @Test
    void supportsFalseDoesNotExecuteTool() {
        AgentToolDefinition definition = AgentToolDefinition.builder()
                .name("get_default_resume")
                .displayName("读取默认简历")
                .description("读取用户默认简历")
                .domain(AgentToolDomain.RESUME)
                .permission(AgentToolPermission.READ_USER_DATA)
                .riskLevel(AgentToolRiskLevel.LOW)
                .build();
        when(registry.findByName("get_default_resume")).thenReturn(Optional.of(readOnlyTool));
        when(readOnlyTool.definition()).thenReturn(definition);
        when(readOnlyTool.supports(any())).thenReturn(false);

        AgentToolResult result = service.execute(context(Map.of()), "get_default_resume");

        assertFalse(result.isSuccess());
        assertEquals("工具不可用", result.getSummary());
        assertEquals("当前上下文不支持该工具", result.getErrorMessage());
        verify(readOnlyTool, never()).execute(any());
    }

    @Test
    void readOnlyToolNotBlockedByParameterValidation() {
        AgentToolDefinition definition = AgentToolDefinition.builder()
                .name("get_default_resume")
                .displayName("读取默认简历")
                .description("读取用户默认简历")
                .domain(AgentToolDomain.RESUME)
                .permission(AgentToolPermission.READ_USER_DATA)
                .riskLevel(AgentToolRiskLevel.LOW)
                .build();
        when(registry.findByName("get_default_resume")).thenReturn(Optional.of(readOnlyTool));
        when(readOnlyTool.definition()).thenReturn(definition);
        when(readOnlyTool.supports(any())).thenReturn(true);
        when(readOnlyTool.execute(org.mockito.ArgumentMatchers.any())).thenReturn(
                AgentToolResult.success("get_default_resume", "已读取", Map.of())
        );

        AgentToolResult result = service.execute(context(Map.of()), "get_default_resume");

        assertTrue(result.isSuccess());
        verify(readOnlyTool).execute(org.mockito.ArgumentMatchers.any());
    }

    private void stubTool(String toolName, AgentToolDefinition definition) {
        when(registry.findByName(toolName)).thenReturn(Optional.of(writeTool));
        when(writeTool.definition()).thenReturn(definition);
        lenient().when(writeTool.supports(any())).thenReturn(true);
    }

    private AgentToolContext context(Map<String, Object> args) {
        return AgentToolContext.builder()
                .userId(1L)
                .sessionId("S-1")
                .userMessage("test message")
                .args(args)
                .build();
    }
}
