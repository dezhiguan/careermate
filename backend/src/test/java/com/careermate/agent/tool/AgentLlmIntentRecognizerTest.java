package com.careermate.agent.tool;

import com.careermate.llm.LlmClient;
import com.careermate.llm.LlmProperties;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentLlmIntentRecognizerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AgentToolRouter fallbackRouter = new AgentToolRouter();

    @Mock
    private AgentToolRegistry toolRegistry;

    @BeforeEach
    void setUpRegistry() {
        when(toolRegistry.knownToolNames()).thenReturn(Set.of(
                "get_default_resume",
                "get_latest_job_match",
                "create_job_match",
                "create_interview_session",
                "get_dashboard_overview",
                "get_career_tasks",
                "create_career_task",
                "mark_career_task_done",
                "search_knowledge_base",
                "generate_resume_from_jd"
        ));
        when(toolRegistry.listDefinitions()).thenReturn(java.util.List.of(
                AgentToolDefinition.builder()
                        .name("get_default_resume")
                        .displayName("读取默认简历")
                        .description("读取用户默认简历")
                        .domain(AgentToolDomain.RESUME)
                        .permission(AgentToolPermission.READ_USER_DATA)
                        .riskLevel(AgentToolRiskLevel.LOW)
                        .build(),
                AgentToolDefinition.builder()
                        .name("get_career_tasks")
                        .displayName("读取任务")
                        .description("读取用户求职任务")
                        .domain(AgentToolDomain.TASK)
                        .permission(AgentToolPermission.READ_USER_DATA)
                        .riskLevel(AgentToolRiskLevel.LOW)
                        .build()
        ));
    }

    private LlmProperties props(String provider) {
        LlmProperties p = new LlmProperties();
        p.setProvider(provider);
        return p;
    }

    private AgentLlmIntentRecognizer recognizer(LlmClient llmClient, String provider) {
        return new AgentLlmIntentRecognizer(llmClient, props(provider), mapper, fallbackRouter, toolRegistry);
    }

    @Test
    void mockProviderUsesFallbackRouter() {
        LlmClient mockLlm = org.mockito.Mockito.mock(LlmClient.class);
        Optional<AgentToolRouter.RoutedTool> r = recognizer(mockLlm, "mock").route("帮我分析简历");
        assertTrue(r.isPresent());
        assertEquals("get_default_resume", r.get().toolName());
        verify(mockLlm, never()).chat(any());
    }

    @Test
    void validLlmJsonReturnsRoutedTool() {
        LlmClient mockLlm = org.mockito.Mockito.mock(LlmClient.class);
        when(mockLlm.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .content("{\"toolName\": \"get_career_tasks\"}")
                        .build());
        Optional<AgentToolRouter.RoutedTool> r = recognizer(mockLlm, "qwen").route("随便聊聊今天天气");
        assertTrue(r.isPresent());
        assertEquals("get_career_tasks", r.get().toolName());
    }

    @Test
    void validLlmJsonWithEmptyArgsObject() {
        LlmClient mockLlm = org.mockito.Mockito.mock(LlmClient.class);
        when(mockLlm.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .content("{\"toolName\": \"get_default_resume\", \"args\": {}}")
                        .build());
        Optional<AgentToolRouter.RoutedTool> r = recognizer(mockLlm, "qwen").route("帮我看看简历");
        assertTrue(r.isPresent());
        assertEquals("get_default_resume", r.get().toolName());
    }

    @Test
    void llmExceptionFallsBackToRegexRouter() {
        LlmClient mockLlm = org.mockito.Mockito.mock(LlmClient.class);
        when(mockLlm.chat(any(ChatRequest.class)))
                .thenThrow(new RuntimeException("network error"));
        Optional<AgentToolRouter.RoutedTool> r = recognizer(mockLlm, "qwen").route("帮我分析简历");
        assertTrue(r.isPresent());
        assertEquals("get_default_resume", r.get().toolName());
    }

    @Test
    void unknownToolFromLlmFallsBackToRegexRouter() {
        LlmClient mockLlm = org.mockito.Mockito.mock(LlmClient.class);
        when(mockLlm.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .content("{\"toolName\": \"unknown_tool_xyz\"}")
                        .build());
        Optional<AgentToolRouter.RoutedTool> r = recognizer(mockLlm, "qwen").route("帮我分析简历");
        assertTrue(r.isPresent());
        assertEquals("get_default_resume", r.get().toolName());
    }
}
