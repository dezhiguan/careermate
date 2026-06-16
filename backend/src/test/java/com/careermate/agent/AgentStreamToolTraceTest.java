package com.careermate.agent;

import com.careermate.agent.dto.AgentTraceResponse;
import com.careermate.agent.runtime.AgentKernelEventTypes;
import com.careermate.agent.runtime.AgentKernelService;
import com.careermate.agent.runtime.AgentRunRequest;
import com.careermate.agent.runtime.AgentRunResult;
import com.careermate.agent.session.AgentSessionService;
import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.AgentToolExecutionService;
import com.careermate.agent.tool.AgentToolResult;
import com.careermate.agent.tool.AgentToolRouter;
import com.careermate.agent.tool.AgentToolTraceSupport;
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

/**
 * 回归：工具命中写入 trace；工具失败仍可将结果注入 prompt（不抛异常阻塞流式任务）。
 */
@SpringBootTest
@ActiveProfiles("test")
class AgentStreamToolTraceTest {

    @Autowired
    private AgentToolRouter agentToolRouter;

    @Autowired
    private AgentToolExecutionService agentToolExecutionService;

    @Autowired
    private AgentSessionService agentSessionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AgentKernelService agentKernelService;

    @Test
    void kernelEnabledRunProducesToolTraceEvents() {
        long userId = 900_003L;
        String sessionId = agentSessionService.createSession(userId).getSessionId();
        AgentRunResult result = agentKernelService.prepareRun(AgentRunRequest.builder()
                .userId(userId)
                .sessionId(sessionId)
                .userMessage("请帮我查看当前求职进展和看板统计数据详情")
                .build());

        assertTrue(result.getEvents().stream().anyMatch(e ->
                AgentKernelEventTypes.TRACE.equals(e.getType())
                        && "get_dashboard_overview".equals(String.valueOf(e.getPayload().get("traceName")))));
        assertTrue(result.getEvents().stream().anyMatch(e ->
                AgentKernelEventTypes.TOOL_START.equals(e.getType())));
        assertTrue(result.getEvents().stream().anyMatch(e ->
                AgentKernelEventTypes.TOOL_RESULT.equals(e.getType())));
    }

    @Test
    void recordsToolTraceOnHitWithSummaryOnly() throws Exception {
        long userId = 900_001L;
        String sessionId = agentSessionService.createSession(userId).getSessionId();
        String userMessage = "帮我分析默认简历";

        AgentToolRouter.RoutedTool routed = agentToolRouter.route(userMessage).orElseThrow();
        assertEquals("get_default_resume", routed.toolName());

        long start = System.currentTimeMillis();
        AgentToolContext context = AgentToolContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .userMessage(userMessage)
                .args(routed.args())
                .build();
        AgentToolResult result = agentToolExecutionService.execute(context, routed.toolName());
        long latencyMs = System.currentTimeMillis() - start;

        assertNotNull(result.getSummary());
        String requestSummary = AgentToolTraceSupport.buildRequestSummary(
                routed.toolName(),
                routed.args(),
                userMessage
        );
        String responseSummary = AgentToolTraceSupport.buildResponseSummary(result, objectMapper);
        agentSessionService.recordTrace(
                userId,
                sessionId,
                routed.toolName(),
                requestSummary,
                responseSummary,
                result.isSuccess() ? "SUCCESS" : "FAILED",
                latencyMs,
                result.isSuccess() ? null : "TOOL_EXEC_FAILED"
        );

        List<AgentTraceResponse> traces = agentSessionService.getTrace(userId, sessionId);
        AgentTraceResponse toolTrace = traces.stream()
                .filter(t -> "get_default_resume".equals(t.getToolName()))
                .findFirst()
                .orElseThrow();
        assertTrue(toolTrace.getLatencyMs() != null && toolTrace.getLatencyMs() >= 0);
        assertTrue(
                "SUCCESS".equals(toolTrace.getStatus()) || "FAILED".equals(toolTrace.getStatus())
        );
        assertFalse(toolTrace.getRequestSummary().contains("x".repeat(500)));
        assertFalse(toolTrace.getResponseSummary().contains("x".repeat(500)));

        String prompt = AgentPromptAssembler.appendToolResult("base prompt", result);
        assertTrue(prompt.contains("工具调用结果："));
        assertTrue(prompt.contains("get_default_resume"));
    }

    @Test
    void toolFailureStillProducesPromptAndFailedTrace() {
        long userId = 900_002L;
        String sessionId = agentSessionService.createSession(userId).getSessionId();
        String jd = "岗位：Java 后端工程师\n公司：co\n招聘要求：" + "Java Spring Boot ".repeat(20);

        AgentToolRouter.RoutedTool routed = agentToolRouter.route(jd).orElseThrow();
        assertEquals("create_job_match", routed.toolName());

        AgentToolContext context = AgentToolContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .userMessage(jd)
                .args(routed.args())
                .build();
        AgentToolResult result = agentToolExecutionService.execute(context, routed.toolName());

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getSummary().contains("失败"));

        agentSessionService.recordTrace(
                userId,
                sessionId,
                routed.toolName(),
                AgentToolTraceSupport.buildRequestSummary(routed.toolName(), routed.args(), jd),
                AgentToolTraceSupport.buildResponseSummary(result, objectMapper),
                "FAILED",
                5L,
                "TOOL_EXEC_FAILED"
        );

        AgentTraceResponse trace = agentSessionService.getTrace(userId, sessionId).stream()
                .filter(t -> "create_job_match".equals(t.getToolName()))
                .findFirst()
                .orElseThrow();
        assertEquals("FAILED", trace.getStatus());
        assertEquals("TOOL_EXEC_FAILED", trace.getErrorCode());
        assertTrue(trace.getRequestSummary().contains("jdContentLength"));
        assertFalse(trace.getRequestSummary().contains("Java Spring Boot Redis"));

        String prompt = AgentPromptAssembler.appendToolResult("system", result);
        assertTrue(prompt.contains("工具调用结果："));
        assertTrue(prompt.contains("错误：") || prompt.contains(result.getSummary()));
    }

    @Test
    void casualGreetingDoesNotRouteTool() {
        assertTrue(agentToolRouter.route("你好").isEmpty());
        assertTrue(agentToolRouter.route("你好，今天怎么样").isEmpty());
    }
}
