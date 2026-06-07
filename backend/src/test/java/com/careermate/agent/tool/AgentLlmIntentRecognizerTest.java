package com.careermate.agent.tool;

import com.careermate.llm.LlmClient;
import com.careermate.llm.LlmProperties;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentLlmIntentRecognizerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AgentToolRouter fallbackRouter = new AgentToolRouter();

    private LlmProperties props(String provider) {
        LlmProperties p = new LlmProperties();
        p.setProvider(provider);
        return p;
    }

    @Test
    void mockProviderUsesFallbackRouter() {
        LlmClient mockLlm = mock(LlmClient.class);
        AgentLlmIntentRecognizer recognizer =
            new AgentLlmIntentRecognizer(mockLlm, props("mock"), mapper, fallbackRouter);
        Optional<AgentToolRouter.RoutedTool> r = recognizer.route("帮我分析简历");
        assertTrue(r.isPresent());
        assertEquals("get_default_resume", r.get().toolName());
        verify(mockLlm, never()).chat(any());
    }

    @Test
    void validLlmJsonReturnsRoutedTool() {
        LlmClient mockLlm = mock(LlmClient.class);
        when(mockLlm.chat(any(ChatRequest.class)))
            .thenReturn(ChatResponse.builder()
                .content("{\"toolName\": \"get_career_tasks\"}")
                .build());
        AgentLlmIntentRecognizer recognizer =
            new AgentLlmIntentRecognizer(mockLlm, props("qwen"), mapper, fallbackRouter);
        Optional<AgentToolRouter.RoutedTool> r = recognizer.route("随便聊聊今天天气");
        assertTrue(r.isPresent());
        assertEquals("get_career_tasks", r.get().toolName());
    }

    @Test
    void validLlmJsonWithEmptyArgsObject() {
        LlmClient mockLlm = mock(LlmClient.class);
        when(mockLlm.chat(any(ChatRequest.class)))
            .thenReturn(ChatResponse.builder()
                .content("{\"toolName\": \"get_default_resume\", \"args\": {}}")
                .build());
        AgentLlmIntentRecognizer recognizer =
            new AgentLlmIntentRecognizer(mockLlm, props("qwen"), mapper, fallbackRouter);
        Optional<AgentToolRouter.RoutedTool> r = recognizer.route("帮我看看简历");
        assertTrue(r.isPresent());
        assertEquals("get_default_resume", r.get().toolName());
    }

    @Test
    void llmExceptionFallsBackToRegexRouter() {
        LlmClient mockLlm = mock(LlmClient.class);
        when(mockLlm.chat(any(ChatRequest.class)))
            .thenThrow(new RuntimeException("network error"));
        AgentLlmIntentRecognizer recognizer =
            new AgentLlmIntentRecognizer(mockLlm, props("qwen"), mapper, fallbackRouter);
        Optional<AgentToolRouter.RoutedTool> r = recognizer.route("帮我分析简历");
        assertTrue(r.isPresent());
        assertEquals("get_default_resume", r.get().toolName());
    }
}
