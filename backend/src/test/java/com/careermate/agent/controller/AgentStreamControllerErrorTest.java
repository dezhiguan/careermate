package com.careermate.agent.controller;

import com.careermate.agent.session.AgentSessionService;
import com.careermate.llm.LlmClient;
import com.careermate.llm.StreamCallback;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.llm.dto.ToolCallRequest;
import com.careermate.llm.dto.ToolCallResponse;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.observability.LlmChatTraceRecorder;
import com.careermate.observability.LlmTracingSupport;
import com.careermate.observability.TracingLlmClient;
import com.careermate.security.CurrentUser;
import com.careermate.security.CurrentUserContext;
import com.careermate.testsupport.TestUserSupport;
import com.careermate.testsupport.TestUsers;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class AgentStreamControllerErrorTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentSessionService agentSessionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        TestUserSupport.ensureTestUsers(userMapper, userProfileMapper, passwordEncoder);
        CurrentUserContext.set(CurrentUser.builder()
                .userId(TestUsers.USER_A)
                .username(TestUsers.USER_A_NAME)
                .role("USER")
                .authenticated(true)
                .build());
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void streamHandlesLlmError() throws Exception {
        String sessionId = agentSessionService.createSession(TestUsers.USER_A).getSessionId();

        MvcResult asyncResult = mockMvc.perform(post("/api/agent/sessions/" + sessionId + "/messages/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("message", "请帮我详细分析当前求职进展和下一步行动建议"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult)).andExpect(status().isOk());

        waitUntil(() -> agentSessionService.getTrace(TestUsers.USER_A, sessionId).stream()
                .anyMatch(t -> "ERROR".equals(t.getToolName())), 30_000);
        assertTrue(agentSessionService.getTrace(TestUsers.USER_A, sessionId).stream()
                .anyMatch(t -> "ERROR".equals(t.getToolName())));
    }

    private void waitUntil(Callable<Boolean> condition, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.call()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Condition not met within " + timeoutMs + "ms");
    }

    @TestConfiguration
    static class ErrorLlmTestConfig {

        @Bean
        @Primary
        LlmClient errorLlmClient(LlmTracingSupport llmTracingSupport, LlmChatTraceRecorder llmChatTraceRecorder) {
            LlmClient delegate = new LlmClient() {
                @Override
                public ChatResponse chat(ChatRequest request) {
                    return ChatResponse.builder()
                            .content("{\"thought\":\"结束\",\"action\":\"final_answer\"}")
                            .build();
                }

                @Override
                public void streamChat(ChatRequest request, StreamCallback callback) {
                    callback.onError(new RuntimeException("LLM unavailable"));
                }

                @Override
                public ToolCallResponse toolCall(ToolCallRequest request) {
                    return ToolCallResponse.builder().content("unused").build();
                }
            };
            return new TracingLlmClient(delegate, llmTracingSupport, llmChatTraceRecorder);
        }
    }
}
