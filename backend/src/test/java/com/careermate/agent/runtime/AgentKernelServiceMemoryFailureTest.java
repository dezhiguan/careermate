package com.careermate.agent.runtime;

import com.careermate.agent.memory.AgentMemoryService;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.security.CurrentUser;
import com.careermate.security.CurrentUserContext;
import com.careermate.testsupport.TestUserSupport;
import com.careermate.testsupport.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class AgentKernelServiceMemoryFailureTest {

    @Autowired
    private AgentKernelService agentKernelService;

    @MockBean
    private AgentMemoryService agentMemoryService;

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
        when(agentMemoryService.loadMemoryContext(anyLong(), any()))
                .thenThrow(new RuntimeException("memory db unavailable"));
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void prepareRunContinuesWhenMemoryLoadFails() {
        AgentRunResult result = agentKernelService.prepareRun(AgentRunRequest.builder()
                .userId(TestUsers.USER_A)
                .sessionId("kernel-memory-failure-session")
                .userMessage("请帮我分析当前求职进展")
                .build());

        assertNotNull(result.getChatRequest());
        assertNotNull(result.getSystemPrompt());
        assertTrue(result.getEvents().stream().anyMatch(this::isFailedMemoryTrace));
    }

    private boolean isFailedMemoryTrace(AgentEvent event) {
        if (!AgentKernelEventTypes.TRACE.equals(event.getType())) {
            return false;
        }
        if (!AgentKernelService.TRACE_MEMORY_CONTEXT_LOADED.equals(
                String.valueOf(event.getPayload().get("traceName")))) {
            return false;
        }
        return "FAILED".equals(String.valueOf(event.getPayload().get("status")));
    }

    private void loginAs(long userId) {
        CurrentUserContext.set(CurrentUser.builder()
                .userId(userId)
                .username(TestUsers.USER_A_NAME)
                .role("USER")
                .authenticated(true)
                .build());
    }
}
