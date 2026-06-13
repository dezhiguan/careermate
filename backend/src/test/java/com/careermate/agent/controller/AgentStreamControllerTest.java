package com.careermate.agent.controller;

import com.careermate.agent.session.AgentSessionService;
import com.careermate.agent.sse.AgentTaskRegistry;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.resume.dto.ResumeCreateRequest;
import com.careermate.resume.service.ResumeService;
import com.careermate.security.CurrentUser;
import com.careermate.security.CurrentUserContext;
import com.careermate.testsupport.TestUserSupport;
import com.careermate.testsupport.TestUsers;
import com.careermate.workspace.support.WorkspaceSessionRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class AgentStreamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentSessionService agentSessionService;

    @Autowired
    private AgentTaskRegistry taskRegistry;

    @Autowired
    private WorkspaceSessionRepository workspaceSessionRepository;

    @Autowired
    private ResumeService resumeService;

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
    void streamCompletesWithAgentReply() throws Exception {
        String sessionId = agentSessionService.createSession(TestUsers.USER_A).getSessionId();

        dispatchStream(sessionId, "请帮我详细分析当前求职进展和下一步行动建议");

        waitUntil(() -> agentSessionService.getSession(TestUsers.USER_A, sessionId)
                .getMessages().stream().anyMatch(m -> "agent".equals(m.getRole())), 30_000);

        var traces = agentSessionService.getTrace(TestUsers.USER_A, sessionId);
        assertTrue(traces.stream().anyMatch(t -> "PLAN".equals(t.getToolName())));
        assertTrue(traces.stream().anyMatch(t -> "DONE".equals(t.getToolName())));
    }

    @Test
    void streamWithResumeSpecialistAndDefaultResume() throws Exception {
        ResumeCreateRequest request = new ResumeCreateRequest();
        request.setTitle("Agent 测试简历");
        request.setContent("# 测试\n- Java\n- Spring Boot");
        resumeService.createResume(request);

        String sessionId = agentSessionService.createSession(TestUsers.USER_A).getSessionId();
        dispatchStream(sessionId, "请帮我优化简历中的项目描述和量化指标");

        waitUntil(() -> agentSessionService.getSession(TestUsers.USER_A, sessionId)
                .getMessages().size() >= 2, 30_000);
    }

    @Test
    void streamWithInterviewSpecialistMessage() throws Exception {
        String sessionId = agentSessionService.createSession(TestUsers.USER_A).getSessionId();
        dispatchStream(sessionId, "请帮我安排一次面试模拟练习并给出反馈");

        waitUntil(() -> !agentSessionService.getTrace(TestUsers.USER_A, sessionId).isEmpty(), 30_000);
    }

    @Test
    void streamWithJobMatchSpecialistMessage() throws Exception {
        String sessionId = agentSessionService.createSession(TestUsers.USER_A).getSessionId();
        dispatchStream(sessionId, "请帮我分析岗位匹配结果和主要技能缺口");

        waitUntil(() -> agentSessionService.getSession(TestUsers.USER_A, sessionId)
                .getMessages().stream().anyMatch(m -> "agent".equals(m.getRole())), 30_000);
    }

    @Test
    void streamWithJdPrepWorkspaceContext() throws Exception {
        var wsSession = workspaceSessionRepository.createJdPrepSession(
                TestUsers.USER_A,
                "jd-stream-1",
                "{\"company\":\"阿里\",\"title\":\"后端开发\"}",
                "阿里 后端"
        );

        dispatchStream(wsSession.getSessionId(), "请根据当前 JD 空间帮我生成定制简历");

        waitUntil(() -> agentSessionService.getSession(TestUsers.USER_A, wsSession.getSessionId())
                .getMessages().size() >= 2, 30_000);
    }

    @Test
    void streamRejectsConcurrentRequest() throws Exception {
        String sessionId = agentSessionService.createSession(TestUsers.USER_A).getSessionId();

        MvcResult first = mockMvc.perform(post("/api/agent/sessions/" + sessionId + "/messages/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMessage("请帮我详细分析当前求职进展和下一步行动建议")))
                .andExpect(request().asyncStarted())
                .andReturn();

        assertTrue(taskRegistry.isRunning(sessionId));

        mockMvc.perform(post("/api/agent/sessions/" + sessionId + "/messages/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMessage("再次发送消息应该被拒绝")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429));

        mockMvc.perform(asyncDispatch(first)).andExpect(status().isOk());
        waitUntil(() -> !taskRegistry.isRunning(sessionId), 30_000);
    }

    @Test
    void streamRoutesDashboardToolWhenNoSpecialistKeyword() throws Exception {
        String sessionId = agentSessionService.createSession(TestUsers.USER_A).getSessionId();
        dispatchStream(sessionId, "请帮我查看当前求职进展和看板统计数据详情");

        waitUntil(() -> agentSessionService.getTrace(TestUsers.USER_A, sessionId).stream()
                .anyMatch(t -> "get_dashboard_overview".equals(t.getToolName())), 30_000);
    }

    private void dispatchStream(String sessionId, String message) throws Exception {
        MvcResult asyncResult = mockMvc.perform(post("/api/agent/sessions/" + sessionId + "/messages/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMessage(message)))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult)).andExpect(status().isOk());
    }

    private String jsonMessage(String message) throws Exception {
        return objectMapper.writeValueAsString(Map.of("message", message));
    }

    private void loginAs(long userId) {
        CurrentUserContext.set(CurrentUser.builder()
                .userId(userId)
                .username(TestUsers.USER_A_NAME)
                .role("USER")
                .authenticated(true)
                .build());
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
