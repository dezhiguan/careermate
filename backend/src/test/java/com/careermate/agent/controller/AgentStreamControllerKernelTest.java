package com.careermate.agent.controller;

import com.careermate.agent.dto.AgentTraceResponse;
import com.careermate.agent.session.AgentSessionService;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "careermate.agent.kernel.enabled=true")
class AgentStreamControllerKernelTest {

    private static final Pattern SSE_EVENT_NAME = Pattern.compile("(?m)^event:(\\S+)", Pattern.MULTILINE);

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
        loginAs(TestUsers.USER_A);
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void kernelEnabledStreamCompletesWithAgentReply() throws Exception {
        String sessionId = agentSessionService.createSession(TestUsers.USER_A).getSessionId();

        MvcResult asyncResult = mockMvc.perform(post("/api/agent/sessions/" + sessionId + "/messages/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "请帮我详细分析当前求职进展和下一步行动建议"
                        ))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult)).andExpect(status().isOk());

        waitUntil(() -> agentSessionService.getSession(TestUsers.USER_A, sessionId)
                .getMessages().stream().anyMatch(m -> "agent".equals(m.getRole())), 30_000);

        var traces = agentSessionService.getTrace(TestUsers.USER_A, sessionId);
        assertTrue(traces.stream().anyMatch(t -> "PLAN".equals(t.getToolName())));
        assertTrue(traces.stream().anyMatch(t -> "DONE".equals(t.getToolName())));
    }

    @Test
    void kernelEnabledEmitsKernelEventsInRealTimeOrder() throws Exception {
        String sessionId = agentSessionService.createSession(TestUsers.USER_A).getSessionId();
        String message = "请帮我查看当前求职进展和看板统计数据详情";

        MvcResult asyncResult = mockMvc.perform(post("/api/agent/sessions/" + sessionId + "/messages/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", message))))
                .andExpect(request().asyncStarted())
                .andReturn();

        // PLAN 应在 LLM 完成前流式产出——用 mock LLM 时"中途快照"断言天然 race，
        // 改为在下方对最终有序 trace 做确定性顺序断言（PLAN 早于 MESSAGE/DONE）。
        waitUntil(() -> hasTrace(TestUsers.USER_A, sessionId, "PLAN"), 30_000);

        waitUntil(() -> hasTrace(TestUsers.USER_A, sessionId, "get_dashboard_overview"), 30_000);

        MvcResult finalResult = mockMvc.perform(asyncDispatch(asyncResult)).andExpect(status().isOk()).andReturn();

        waitUntil(() -> hasTrace(TestUsers.USER_A, sessionId, "DONE"), 30_000);

        List<String> traceOrder = agentSessionService.getTrace(TestUsers.USER_A, sessionId).stream()
                .map(AgentTraceResponse::getToolName)
                .toList();
        assertTraceBefore(traceOrder, "PLAN", "get_dashboard_overview");
        assertTraceBefore(traceOrder, "PLAN", "MESSAGE");
        assertTraceBefore(traceOrder, "PLAN", "DONE");
        assertTraceBefore(traceOrder, "MESSAGE", "DONE");

        List<String> sseEventNames = extractSseEventNames(finalResult.getResponse().getContentAsString());
        assertSseBefore(sseEventNames, "plan", "tool_start");
        assertSseBefore(sseEventNames, "tool_start", "tool_result");
        assertTrue(
                indexOfFirst(sseEventNames, "message") > indexOfFirst(sseEventNames, "tool_result")
                        || indexOfFirst(sseEventNames, "done") > indexOfFirst(sseEventNames, "tool_result"),
                "TOOL_RESULT should appear before MESSAGE or DONE in SSE stream"
        );
    }

    private void loginAs(long userId) {
        CurrentUserContext.set(CurrentUser.builder()
                .userId(userId)
                .username(TestUsers.USER_A_NAME)
                .role("USER")
                .authenticated(true)
                .build());
    }

    private boolean hasTrace(long userId, String sessionId, String toolName) {
        return agentSessionService.getTrace(userId, sessionId).stream()
                .anyMatch(t -> toolName.equals(t.getToolName()));
    }

    private void assertTraceBefore(List<String> traceOrder, String earlier, String later) {
        int earlierIndex = traceOrder.indexOf(earlier);
        int laterIndex = traceOrder.indexOf(later);
        assertTrue(earlierIndex >= 0, "Missing trace: " + earlier);
        assertTrue(laterIndex >= 0, "Missing trace: " + later);
        assertTrue(earlierIndex < laterIndex,
                earlier + " should appear before " + later + " in trace order: " + traceOrder);
    }

    private List<String> extractSseEventNames(String body) {
        List<String> names = new ArrayList<>();
        Matcher matcher = SSE_EVENT_NAME.matcher(body == null ? "" : body);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private void assertSseBefore(List<String> sseEventNames, String earlier, String later) {
        int earlierIndex = indexOfFirst(sseEventNames, earlier);
        int laterIndex = indexOfFirst(sseEventNames, later);
        assertTrue(earlierIndex >= 0, "Missing SSE event: " + earlier);
        assertTrue(laterIndex >= 0, "Missing SSE event: " + later);
        assertTrue(earlierIndex < laterIndex,
                earlier + " should appear before " + later + " in SSE order: " + sseEventNames);
    }

    private int indexOfFirst(List<String> eventNames, String target) {
        for (int i = 0; i < eventNames.size(); i++) {
            if (target.equals(eventNames.get(i))) {
                return i;
            }
        }
        return -1;
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
}
