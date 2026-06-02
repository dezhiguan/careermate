package com.careermate.agent.session;

import com.careermate.security.CurrentUser;
import com.careermate.security.CurrentUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class AgentSessionApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentSessionService agentSessionService;

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
        loginAs(1L, "local-user");

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
        loginAs(1L, "local-user");
        String sessionId = agentSessionService.createSession(1L).getSessionId();

        agentSessionService.appendMessage(1L, sessionId, "user", "帮我分析简历", "text");
        agentSessionService.appendMessage(1L, sessionId, "agent", "mock reply", "text");
        agentSessionService.recordTrace(1L, sessionId, "PLAN", "{}", "{}", "SUCCESS", null, null);
        agentSessionService.recordTrace(1L, sessionId, "MESSAGE", "{}", "{}", "SUCCESS", null, null);
        agentSessionService.recordTrace(1L, sessionId, "DONE", "{}", "{}", "SUCCESS", 100L, null);
        agentSessionService.markCompleted(1L, sessionId, 100L);

        var session = agentSessionService.getSession(1L, sessionId);
        assertEquals(2, session.getMessages().size());
        assertEquals("COMPLETED", session.getStatus());

        var traces = agentSessionService.getTrace(1L, sessionId);
        assertEquals(3, traces.size());
    }

    @Test
    void otherUserGetsNotFound() throws Exception {
        loginAs(1L, "local-user");
        String sessionId = agentSessionService.createSession(1L).getSessionId();

        loginAs(999999L, "other");
        mockMvc.perform(get("/api/agent/sessions/" + sessionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }
}
