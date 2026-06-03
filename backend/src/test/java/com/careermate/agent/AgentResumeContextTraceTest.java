package com.careermate.agent;

import com.careermate.agent.dto.AgentTraceResponse;
import com.careermate.agent.session.AgentSessionService;
import com.careermate.resume.ResumeContext;
import com.careermate.resume.ResumeContextProvider;
import com.careermate.resume.ResumeService;
import com.careermate.resume.dto.ResumeCreateRequest;
import com.careermate.security.CurrentUser;
import com.careermate.security.CurrentUserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AgentResumeContextTraceTest {

    @Autowired
    private ResumeContextProvider resumeContextProvider;

    @Autowired
    private AgentSessionService agentSessionService;

    @Autowired
    private ResumeService resumeService;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void recordsResumeContextTracePayloadWithoutFullContent() throws Exception {
        long userId = 1L;
        loginAs(userId, "local-user");
        String title = "TraceResume-" + System.nanoTime();
        ResumeCreateRequest request = new ResumeCreateRequest();
        request.setTitle(title);
        request.setContent("正文不应进入 trace payload");
        var created = resumeService.createResume(request);
        resumeService.setDefaultResume(created.getId());

        ResumeContext ctx = resumeContextProvider.getResumeContext(userId);
        assertTrue(ctx.isAvailable());

        String sessionId = agentSessionService.createSession(userId).getSessionId();
        Map<String, Object> payload = Map.of(
                "resumeId", ctx.getResumeId(),
                "title", ctx.getTitle(),
                "contentLength", ctx.getContent().length(),
                "message", "已加载默认简历：" + ctx.getTitle()
        );
        agentSessionService.recordTrace(
                userId,
                sessionId,
                "resume_context",
                "{}",
                objectMapper.writeValueAsString(payload),
                "SUCCESS",
                null,
                null
        );

        List<AgentTraceResponse> traces = agentSessionService.getTrace(userId, sessionId);
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
