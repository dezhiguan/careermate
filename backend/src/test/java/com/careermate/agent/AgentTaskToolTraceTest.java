package com.careermate.agent;

import com.careermate.agent.dto.AgentTraceResponse;
import com.careermate.agent.session.AgentSessionService;
import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.AgentToolExecutionService;
import com.careermate.agent.tool.AgentToolResult;
import com.careermate.agent.tool.AgentToolRouter;
import com.careermate.agent.tool.AgentToolTraceSupport;
import com.careermate.mapper.AgentMessageMapper;
import com.careermate.mapper.AgentSessionMapper;
import com.careermate.mapper.AgentTaskStateMapper;
import com.careermate.mapper.AgentToolCallMapper;
import com.careermate.mapper.CareerTaskMapper;
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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AgentTaskToolTraceTest {

    @Autowired
    private AgentToolRouter agentToolRouter;

    @Autowired
    private AgentToolExecutionService agentToolExecutionService;

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
    private CareerTaskMapper careerTaskMapper;

    @Autowired
    private AgentSessionMapper agentSessionMapper;

    @Autowired
    private AgentMessageMapper agentMessageMapper;

    @Autowired
    private AgentToolCallMapper agentToolCallMapper;

    @Autowired
    private AgentTaskStateMapper agentTaskStateMapper;

    @BeforeEach
    void setUp() {
        TestUserSupport.ensureTestUsers(userMapper, userProfileMapper, passwordEncoder);
        TestUserSupport.cleanupAgentAndTaskUserData(
                careerTaskMapper,
                agentSessionMapper,
                agentMessageMapper,
                agentToolCallMapper,
                agentTaskStateMapper
        );
    }

    @Test
    void recordsCreateCareerTaskTraceWithRealToolName() throws Exception {
        long userId = TestUsers.USER_A;
        String sessionId = agentSessionService.createSession(userId).getSessionId();
        String userMessage = "帮我创建一个任务：E2E trace 任务";

        AgentToolRouter.RoutedTool routed = agentToolRouter.route(userMessage).orElseThrow();
        assertEquals("create_career_task", routed.toolName());

        AgentToolContext context = AgentToolContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .userMessage(userMessage)
                .args(routed.args())
                .build();
        AgentToolResult result = agentToolExecutionService.execute(context, routed.toolName());

        String responseSummary = AgentToolTraceSupport.buildResponseSummary(result, objectMapper);
        agentSessionService.recordTrace(
                userId,
                sessionId,
                routed.toolName(),
                AgentToolTraceSupport.buildRequestSummary(routed.toolName(), routed.args(), userMessage),
                responseSummary,
                result.isSuccess() ? "SUCCESS" : "FAILED",
                1L,
                null
        );

        List<AgentTraceResponse> traces = agentSessionService.getTrace(userId, sessionId);
        assertFalse(traces.isEmpty());
        AgentTraceResponse trace = traces.get(traces.size() - 1);
        assertEquals("create_career_task", trace.getToolName());

        JsonNode response = objectMapper.readTree(trace.getResponseSummary());
        assertTrue(result.isSuccess());
        assertTrue(response.path("summary").asText().contains("已创建任务"));
        assertTrue(response.path("data").path("taskId").isNumber());
        assertTrue(response.path("data").path("title").asText().contains("E2E"));
        assertFalse(trace.getRequestSummary().contains("【"));
    }

    @Test
    void failedMarkDoneTraceDoesNotIncludeFullPrompt() throws Exception {
        long userId = TestUsers.USER_B;
        String sessionId = agentSessionService.createSession(userId).getSessionId();
        String userMessage = "不存在的任务标题已经做完了";

        AgentToolRouter.RoutedTool routed = agentToolRouter.route(userMessage).orElseThrow();
        AgentToolContext context = AgentToolContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .userMessage(userMessage)
                .args(routed.args())
                .build();
        AgentToolResult result = agentToolExecutionService.execute(context, routed.toolName());
        assertFalse(result.isSuccess());

        String requestSummary = AgentToolTraceSupport.buildRequestSummary(
                routed.toolName(),
                routed.args(),
                userMessage
        );
        String responseSummary = AgentToolTraceSupport.buildResponseSummary(result, objectMapper);
        agentSessionService.recordTrace(
                userId,
                sessionId,
                "mark_career_task_done",
                requestSummary,
                responseSummary,
                "FAILED",
                1L,
                "TOOL_EXEC_FAILED"
        );

        assertFalse(requestSummary.contains("不存在的任务标题已经做完了"));
        assertTrue(requestSummary.contains("userMessageLength"));
        assertEquals("mark_career_task_done", agentSessionService.getTrace(userId, sessionId).get(0).getToolName());
    }
}
