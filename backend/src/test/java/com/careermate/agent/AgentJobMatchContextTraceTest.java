package com.careermate.agent;

import com.careermate.agent.dto.AgentTraceResponse;
import com.careermate.agent.session.AgentSessionService;
import com.careermate.jobmatch.JobMatchContext;
import com.careermate.jobmatch.JobMatchContextProvider;
import com.careermate.jobmatch.JobMatchJsonSupport;
import com.careermate.jobmatch.service.JobMatchService;
import com.careermate.mapper.JobMatchMapper;
import com.careermate.mapper.ResumeMapper;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.model.entity.JobMatchEntity;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AgentJobMatchContextTraceTest {

    @Autowired
    private JobMatchContextProvider jobMatchContextProvider;

    @Autowired
    private AgentSessionService agentSessionService;

    @Autowired
    private JobMatchMapper jobMatchMapper;

    @Autowired
    private JobMatchJsonSupport jobMatchJsonSupport;

    @Autowired
    private ResumeMapper resumeMapper;

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
    void recordsJobMatchContextTraceWithoutJdBody() throws Exception {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        insertMatch("test_trace_job", "very long jd content should not appear in trace payload");

        JobMatchContext ctx = jobMatchContextProvider.getLatestJobMatchContext(TestUsers.USER_A);
        assertTrue(ctx.isAvailable());

        String sessionId = agentSessionService.createSession(TestUsers.USER_A).getSessionId();
        Map<String, Object> payload = Map.of(
                "jobMatchId", ctx.getJobMatchId(),
                "jobTitle", ctx.getJobTitle(),
                "companyName", ctx.getCompanyName(),
                "matchScore", ctx.getMatchScore(),
                "matchLevel", ctx.getMatchLevel(),
                "matchedSkillsCount", ctx.getMatchedSkills().size(),
                "missingSkillsCount", ctx.getMissingSkills().size(),
                "message", "已加载最近岗位匹配：" + ctx.getJobTitle()
        );
        agentSessionService.recordTrace(
                TestUsers.USER_A,
                sessionId,
                "job_match_context",
                "{}",
                objectMapper.writeValueAsString(payload),
                "SUCCESS",
                null,
                null
        );

        List<AgentTraceResponse> traces = agentSessionService.getTrace(TestUsers.USER_A, sessionId);
        AgentTraceResponse trace = traces.stream()
                .filter(t -> "job_match_context".equals(t.getToolName()))
                .findFirst()
                .orElseThrow();

        JsonNode summary = objectMapper.readTree(trace.getResponseSummary());
        assertEquals("test_trace_job", summary.path("jobTitle").asText());
        assertTrue(!trace.getResponseSummary().contains("very long jd"));
    }

    private void insertMatch(String jobTitle, String jdContent) {
        OffsetDateTime now = OffsetDateTime.now();
        JobMatchEntity entity = new JobMatchEntity();
        entity.setUserId(TestUsers.USER_A);
        entity.setJobTitle(jobTitle);
        entity.setCompanyName("test_co");
        entity.setJdContent(jdContent);
        entity.setMatchScore(60);
        entity.setMatchLevel("MEDIUM");
        entity.setMatchedSkills(jobMatchJsonSupport.writeStringList(List.of("Java")));
        entity.setMissingSkills(jobMatchJsonSupport.writeStringList(List.of("Docker")));
        entity.setStrengths(jobMatchJsonSupport.writeStringList(List.of("s1")));
        entity.setRisks(jobMatchJsonSupport.writeStringList(List.of("r1")));
        entity.setSuggestions(jobMatchJsonSupport.writeStringList(List.of("u1", "u2", "u3")));
        entity.setAnalysisSummary("summary");
        entity.setStatus(JobMatchService.STATUS_ACTIVE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        jobMatchMapper.insert(entity);
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
