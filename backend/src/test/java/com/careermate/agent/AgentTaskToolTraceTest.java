package com.careermate.agent;

import com.careermate.agent.dto.AgentTraceResponse;
import com.careermate.agent.session.AgentSessionService;
import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.AgentToolExecutionService;
import com.careermate.agent.tool.AgentToolResult;
import com.careermate.agent.tool.AgentToolRouter;
import com.careermate.agent.tool.AgentToolTraceSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void recordsCreateCareerTaskTraceWithRealToolName() throws Exception {
        long userId = 900_002L;
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
        long userId = 900_003L;
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
