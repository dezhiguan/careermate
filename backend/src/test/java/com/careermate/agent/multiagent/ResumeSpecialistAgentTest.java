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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeSpecialistAgentTest {

    @Mock
    private AgentToolExecutionService toolExecutionService;

    private ResumeSpecialistAgent agent;
    private AgentToolContext context;

    @BeforeEach
    void setUp() {
        agent = new ResumeSpecialistAgent(toolExecutionService);
        context = AgentToolContext.builder().userId(1L).sessionId("S-1").build();
    }

    @Test
    void defaultResumeToolOnSimpleMessage() {
        when(toolExecutionService.execute(context, "get_default_resume"))
                .thenReturn(success("get_default_resume", "已读取"));

        SpecialistResult result = agent.process(context, "看看我的简历");

        assertTrue(result.success());
        assertEquals("get_default_resume", result.toolName());
    }

    @Test
    void searchKnowledgeBaseWhenAskedForAdvice() {
        when(toolExecutionService.execute(context, "search_knowledge_base"))
                .thenReturn(success("search_knowledge_base", "找到 3 条"));

        SpecialistResult result = agent.process(context, "给我一些知识库里的推荐建议");

        assertEquals("search_knowledge_base", result.toolName());
    }

    @Test
    void generateResumeFromJdWhenRequested() {
        when(toolExecutionService.execute(context, "generate_resume_from_jd"))
                .thenReturn(success("generate_resume_from_jd", "已生成"));

        SpecialistResult result = agent.process(context, "请按 JD 生成定制简历");

        assertEquals("generate_resume_from_jd", result.toolName());
    }

    @Test
    void failedToolExecutionReturnsFailedResult() {
        when(toolExecutionService.execute(context, "get_default_resume"))
                .thenReturn(AgentToolResult.builder()
                        .toolName("get_default_resume")
                        .success(false)
                        .summary("无默认简历")
                        .errorMessage("请先创建简历")
                        .build());

        SpecialistResult result = agent.process(context, "读取简历");

        assertFalse(result.success());
        assertTrue(result.toolSummary().contains("请先创建简历"));
    }

    @Test
    void exceptionReturnsFailedResult() {
        when(toolExecutionService.execute(eq(context), eq("get_default_resume")))
                .thenThrow(new RuntimeException("db down"));

        SpecialistResult result = agent.process(context, "读取简历");

        assertFalse(result.success());
        assertTrue(result.toolSummary().contains("db down"));
    }

    @Test
    void shouldGenerateResumeFromJdDetectsKeywords() {
        assertTrue(ResumeSpecialistAgent.shouldGenerateResumeFromJd("请生成简历 pdf"));
        assertTrue(ResumeSpecialistAgent.shouldGenerateResumeFromJd("帮我优化简历并重写"));
        assertFalse(ResumeSpecialistAgent.shouldGenerateResumeFromJd(null));
        assertFalse(ResumeSpecialistAgent.shouldGenerateResumeFromJd("你好"));
    }

    private static AgentToolResult success(String tool, String summary) {
        return AgentToolResult.builder().toolName(tool).success(true).summary(summary).build();
    }
}
