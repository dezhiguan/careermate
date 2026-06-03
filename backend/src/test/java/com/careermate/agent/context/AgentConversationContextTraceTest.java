package com.careermate.agent.context;

import com.careermate.agent.dto.AgentTraceResponse;
import com.careermate.agent.session.AgentSessionService;
import com.careermate.mapper.JobMatchMapper;
import com.careermate.mapper.ResumeMapper;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.testsupport.TestUserSupport;
import com.careermate.testsupport.TestUsers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AgentConversationContextTraceTest {

    @Autowired
    private AgentConversationContextProvider conversationContextProvider;

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

    @Autowired
    private ResumeMapper resumeMapper;

    @Autowired
    private JobMatchMapper jobMatchMapper;

    @BeforeEach
    void setUp() {
        TestUserSupport.ensureTestUsers(userMapper, userProfileMapper, passwordEncoder);
        TestUserSupport.cleanupUserBusinessData(resumeMapper, jobMatchMapper);
    }

    @Test
    void recordsSuccessTraceWithSummaryOnly() throws Exception {
        long userId = TestUsers.USER_A;
        String sessionId = agentSessionService.createSession(userId).getSessionId();
        String secret = "trace_secret_goal_java_backend_" + System.nanoTime();
        agentSessionService.appendMessage(userId, sessionId, "user", secret, "text");
        agentSessionService.appendMessage(userId, sessionId, "agent", "好的", "text");
        agentSessionService.appendMessage(userId, sessionId, "user", "继续", "text");

        ConversationContextResult result = conversationContextProvider.load(userId, sessionId, "继续");
        assertTrue(result.isAvailable());
        recordConversationContextTrace(userId, sessionId, result);

        AgentTraceResponse trace = findConversationTrace(userId, sessionId);
        assertEquals("SUCCESS", trace.getStatus());
        JsonNode summary = objectMapper.readTree(trace.getResponseSummary());
        assertTrue(summary.path("messageCount").asInt() > 0);
        assertTrue(summary.path("charCount").asInt() > 0);
        assertFalse(trace.getResponseSummary().contains(secret));
        assertFalse(trace.getRequestSummary().contains(secret));
    }

    @Test
    void recordsEmptyTraceWhenNoHistory() throws Exception {
        long userId = TestUsers.USER_B;
        String sessionId = agentSessionService.createSession(userId).getSessionId();
        agentSessionService.appendMessage(userId, sessionId, "user", "首条消息", "text");

        ConversationContextResult result = conversationContextProvider.load(userId, sessionId, "首条消息");
        assertFalse(result.isAvailable());
        recordConversationContextTrace(userId, sessionId, result);

        AgentTraceResponse trace = findConversationTrace(userId, sessionId);
        assertEquals("EMPTY", trace.getStatus());
        JsonNode summary = objectMapper.readTree(trace.getResponseSummary());
        assertEquals(0, summary.path("messageCount").asInt());
        assertEquals(0, summary.path("charCount").asInt());
    }

    private void recordConversationContextTrace(
            Long userId,
            String sessionId,
            ConversationContextResult result
    ) throws Exception {
        String status = result.isAvailable() ? "SUCCESS" : "EMPTY";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageCount", result.getMessageCount());
        payload.put("charCount", result.getCharCount());
        agentSessionService.recordTrace(
                userId,
                sessionId,
                "conversation_context",
                "{}",
                objectMapper.writeValueAsString(payload),
                status,
                null,
                null
        );
    }

    private AgentTraceResponse findConversationTrace(Long userId, String sessionId) {
        List<AgentTraceResponse> traces = agentSessionService.getTrace(userId, sessionId);
        return traces.stream()
                .filter(t -> "conversation_context".equals(t.getToolName()))
                .reduce((first, second) -> second)
                .orElseThrow();
    }
}
