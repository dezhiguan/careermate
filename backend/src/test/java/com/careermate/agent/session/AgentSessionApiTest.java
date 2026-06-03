package com.careermate.agent.session;

import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.security.CurrentUser;
import com.careermate.security.CurrentUserContext;
import com.careermate.testsupport.TestUserSupport;
import com.careermate.testsupport.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class AgentSessionApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentSessionService agentSessionService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        TestUserSupport.ensureTestUsers(userMapper, userProfileMapper, passwordEncoder);
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    private void loginAs(long userId, String username) {
        CurrentUserContext.set(CurrentUser.builder()
                .userId(userId)
                .username(username)
                .role("USER")
                .authenticated(true)
                .build());
    }

    @Test
    void createAndGetSession() throws Exception {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);

        String sessionId = mockMvc.perform(post("/api/agent/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"sessionId\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/agent/sessions/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.messages").isArray());

        mockMvc.perform(get("/api/agent/sessions/" + sessionId + "/trace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void persistMessagesAndTraceViaService() {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        String sessionId = agentSessionService.createSession(TestUsers.USER_A).getSessionId();

        agentSessionService.appendMessage(TestUsers.USER_A, sessionId, "user", "帮我分析简历", "text");
        agentSessionService.appendMessage(TestUsers.USER_A, sessionId, "agent", "mock reply", "text");
        agentSessionService.recordTrace(TestUsers.USER_A, sessionId, "PLAN", "{}", "{}", "SUCCESS", null, null);
        agentSessionService.recordTrace(TestUsers.USER_A, sessionId, "MESSAGE", "{}", "{}", "SUCCESS", null, null);
        agentSessionService.recordTrace(TestUsers.USER_A, sessionId, "DONE", "{}", "{}", "SUCCESS", 100L, null);
        agentSessionService.markCompleted(TestUsers.USER_A, sessionId, 100L);

        var session = agentSessionService.getSession(TestUsers.USER_A, sessionId);
        assertEquals(2, session.getMessages().size());
        assertEquals("COMPLETED", session.getStatus());

        var traces = agentSessionService.getTrace(TestUsers.USER_A, sessionId);
        assertEquals(3, traces.size());
    }

    @Test
    void listRecentSessionsForCurrentUserOnly() throws Exception {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        String sessionA = agentSessionService.createSession(TestUsers.USER_A).getSessionId();
        agentSessionService.appendMessage(TestUsers.USER_A, sessionA, "user", "帮我分析默认简历", "text");
        agentSessionService.markCompleted(TestUsers.USER_A, sessionA, 50L);

        String sessionB = agentSessionService.createSession(TestUsers.USER_A).getSessionId();
        agentSessionService.appendMessage(TestUsers.USER_A, sessionB, "user", "看一下我的求职进展", "text");

        loginAs(TestUsers.USER_B, TestUsers.USER_B_NAME);
        String sessionOtherUser = agentSessionService.createSession(TestUsers.USER_B).getSessionId();

        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        mockMvc.perform(get("/api/agent/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[?(@.sessionId == '" + sessionB + "')].title")
                        .value("看一下我的求职进展"))
                .andExpect(jsonPath("$.data[?(@.sessionId == '" + sessionB + "')].status")
                        .value("CREATED"))
                .andExpect(jsonPath("$.data[?(@.sessionId == '" + sessionA + "')].status")
                        .value("COMPLETED"))
                .andExpect(jsonPath("$.data[?(@.sessionId == '" + sessionOtherUser + "')]")
                        .isEmpty());
    }

    @Test
    void otherUserGetsNotFound() throws Exception {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        String sessionId = agentSessionService.createSession(TestUsers.USER_A).getSessionId();

        loginAs(TestUsers.USER_B, TestUsers.USER_B_NAME);
        mockMvc.perform(get("/api/agent/sessions/" + sessionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }
}
