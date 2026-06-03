package com.careermate.agent;

import com.careermate.agent.dto.AgentTraceResponse;
import com.careermate.agent.session.AgentSessionService;
import com.careermate.mapper.JobMatchMapper;
import com.careermate.mapper.ResumeMapper;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.resume.ResumeContext;
import com.careermate.resume.ResumeContextProvider;
import com.careermate.resume.ResumeService;
import com.careermate.resume.dto.ResumeCreateRequest;
import com.careermate.security.CurrentUser;
import com.careermate.security.CurrentUserContext;
import com.careermate.testsupport.TestUserSupport;
import com.careermate.testsupport.TestUsers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AgentResumeContextTraceTest {

    @Autowired
    private ResumeContextProvider resumeContextProvider;

    @Autowired
    private AgentSessionService agentSessionService;

    @Autowired
    private ResumeService resumeService;

    @Autowired
    private ResumeMapper resumeMapper;

    @Autowired
    private JobMatchMapper jobMatchMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        TestUserSupport.ensureTestUsers(userMapper, userProfileMapper, passwordEncoder);
        TestUserSupport.cleanupUserBusinessData(resumeMapper, jobMatchMapper);
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void recordsResumeContextTracePayloadWithoutFullContent() throws Exception {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        String title = "test_trace_" + System.nanoTime();
        ResumeCreateRequest request = new ResumeCreateRequest();
        request.setTitle(title);
        request.setContent("test 正文不应进入 trace payload");
        var created = resumeService.createResume(request);
        resumeService.setDefaultResume(created.getId());

        ResumeContext ctx = resumeContextProvider.getResumeContext(TestUsers.USER_A);
        assertTrue(ctx.isAvailable());

        String sessionId = agentSessionService.createSession(TestUsers.USER_A).getSessionId();
        Map<String, Object> payload = Map.of(
                "resumeId", ctx.getResumeId(),
                "title", ctx.getTitle(),
                "contentLength", ctx.getContent().length(),
                "message", "已加载默认简历：" + ctx.getTitle()
        );
        agentSessionService.recordTrace(
                TestUsers.USER_A,
                sessionId,
                "resume_context",
                "{}",
                objectMapper.writeValueAsString(payload),
                "SUCCESS",
                null,
                null
        );

        List<AgentTraceResponse> traces = agentSessionService.getTrace(TestUsers.USER_A, sessionId);
        AgentTraceResponse resumeTrace = traces.stream()
                .filter(t -> "resume_context".equals(t.getToolName()))
                .findFirst()
                .orElseThrow();

        assertEquals("SUCCESS", resumeTrace.getStatus());
        JsonNode summary = objectMapper.readTree(resumeTrace.getResponseSummary());
        assertEquals(title, summary.path("title").asText());
        assertTrue(summary.path("contentLength").asInt() > 0);
        assertTrue(summary.path("message").asText().contains(title));
        assertTrue(!resumeTrace.getResponseSummary().contains("正文不应进入 trace"));
    }

    private void loginAs(long userId, String username) {
        CurrentUserContext.set(CurrentUser.builder()
                .userId(userId)
                .username(username)
                .role("USER")
                .authenticated(true)
                .build());
    }
}
