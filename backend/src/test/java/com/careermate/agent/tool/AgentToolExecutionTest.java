package com.careermate.agent.tool;

import com.careermate.agent.dto.AgentTraceResponse;
import com.careermate.agent.session.AgentSessionService;
import com.careermate.jobmatch.JobMatchJsonSupport;
import com.careermate.jobmatch.service.JobMatchService;
import com.careermate.mapper.InterviewQuestionMapper;
import com.careermate.mapper.InterviewSessionMapper;
import com.careermate.mapper.JobMatchMapper;
import com.careermate.mapper.ResumeMapper;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.model.entity.JobMatchEntity;
import com.careermate.resume.service.ResumeService;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AgentToolExecutionTest {

    @Autowired
    private AgentToolExecutionService agentToolExecutionService;

    @Autowired
    private AgentToolRouter agentToolRouter;

    @Autowired
    private AgentSessionService agentSessionService;

    @Autowired
    private ResumeService resumeService;

    @Autowired
    private JobMatchMapper jobMatchMapper;

    @Autowired
    private JobMatchJsonSupport jobMatchJsonSupport;

    @Autowired
    private ResumeMapper resumeMapper;

    @Autowired
    private InterviewSessionMapper interviewSessionMapper;

    @Autowired
    private InterviewQuestionMapper interviewQuestionMapper;

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
        TestUserSupport.cleanupUserBusinessData(
                resumeMapper,
                jobMatchMapper,
                interviewSessionMapper,
                interviewQuestionMapper
        );
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void getDefaultResumeReturnsUnavailableWhenEmpty() {
        AgentToolResult result = executeForUser(TestUsers.USER_A, "查看简历", "get_default_resume", Map.of());
        assertTrue(result.isSuccess());
        assertEquals(false, result.getData().get("available"));
    }

    @Test
    void getDefaultResumeReturnsDataForOwner() {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        createDefaultResume("tool_resume", "Java Spring Boot 项目经验");

        AgentToolResult result = executeForUser(TestUsers.USER_A, "分析简历", "get_default_resume", Map.of());
        assertTrue(result.isSuccess());
        assertEquals(true, result.getData().get("available"));
        assertEquals("tool_resume", result.getData().get("title"));
        assertTrue(((Number) result.getData().get("contentLength")).intValue() > 0);
        assertTrue(!String.valueOf(result.getData().get("contentPreview")).contains("x".repeat(600)));
    }

    @Test
    void getLatestJobMatchIsolatedByUser() {
        insertMatch(TestUsers.USER_A, "user_a_job");
        AgentToolResult userB = executeForUser(TestUsers.USER_B, "最近岗位", "get_latest_job_match", Map.of());
        assertTrue(userB.isSuccess());
        assertEquals(false, userB.getData().get("available"));

        AgentToolResult userA = executeForUser(TestUsers.USER_A, "最近岗位", "get_latest_job_match", Map.of());
        assertTrue(userA.isSuccess());
        assertEquals(true, userA.getData().get("available"));
        assertEquals("user_a_job", userA.getData().get("jobTitle"));
    }

    @Test
    void createJobMatchFailsWithoutDefaultResume() {
        String jd = "岗位：Java 后端工程师\n招聘要求：" + "Java Spring Boot Redis Docker ".repeat(5);
        AgentToolResult result = executeForUser(
                TestUsers.USER_A,
                jd,
                "create_job_match",
                agentToolRouter.route(jd).orElseThrow().args()
        );
        assertFalse(result.isSuccess());
    }

    @Test
    void createJobMatchSucceedsWithDefaultResume() {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        createDefaultResume("match_resume", "熟悉 Java 与 Spring Boot");
        String jd = "岗位：Java 后端工程师\n公司：test_co\n招聘要求："
                + "Java Spring Boot Redis Docker Elasticsearch Kubernetes ";
        Map<String, Object> args = agentToolRouter.route(jd).orElseThrow().args();

        AgentToolResult result = executeForUser(TestUsers.USER_A, jd, "create_job_match", args);
        assertTrue(result.isSuccess());
        assertTrue(result.getData().get("jobMatchId") != null);
    }

    @Test
    void getDashboardOverviewReturnsStats() {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        createDefaultResume("dash_resume", "content");
        AgentToolResult result = executeForUser(
                TestUsers.USER_A,
                "求职进展",
                "get_dashboard_overview",
                Map.of()
        );
        assertTrue(result.isSuccess());
        assertTrue(((Number) result.getData().get("resumeCount")).intValue() >= 1);
        assertTrue(result.getData().containsKey("suggestions"));
    }

    @Test
    void createInterviewSessionFailsWithoutDefaultResume() {
        AgentToolResult result = executeForUser(
                TestUsers.USER_B,
                "面试训练",
                "create_interview_session",
                Map.of()
        );
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("简历"));
    }

    @Test
    void recordsToolTraceWithoutFullJdBody() throws Exception {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        createDefaultResume("trace_resume", "content");
        String secretJd = "岗位：Secret Job\n招聘要求：" + "SECRET_JD_MARKER ".repeat(20);
        Map<String, Object> args = agentToolRouter.route(secretJd).orElseThrow().args();
        String sessionId = agentSessionService.createSession(TestUsers.USER_A).getSessionId();

        AgentToolResult result = executeForUser(TestUsers.USER_A, secretJd, "create_job_match", args);
        assertTrue(result.isSuccess());

        String requestSummary = AgentToolTraceSupport.buildRequestSummary(
                "create_job_match",
                args,
                secretJd
        );
        String responseSummary = AgentToolTraceSupport.buildResponseSummary(result, objectMapper);
        agentSessionService.recordTrace(
                TestUsers.USER_A,
                sessionId,
                "create_job_match",
                requestSummary,
                responseSummary,
                "SUCCESS",
                10L,
                null
        );

        List<AgentTraceResponse> traces = agentSessionService.getTrace(TestUsers.USER_A, sessionId);
        AgentTraceResponse trace = traces.stream()
                .filter(t -> "create_job_match".equals(t.getToolName()))
                .findFirst()
                .orElseThrow();
        assertTrue(!trace.getRequestSummary().contains("SECRET_JD_MARKER"));
        JsonNode req = objectMapper.readTree(trace.getRequestSummary());
        assertTrue(req.path("args").path("jdContentLength").asInt() > 80);
    }

    private AgentToolResult executeForUser(
            Long userId,
            String userMessage,
            String toolName,
            Map<String, Object> args
    ) {
        AgentToolContext context = AgentToolContext.builder()
                .userId(userId)
                .sessionId("test-session")
                .userMessage(userMessage)
                .args(args)
                .build();
        return agentToolExecutionService.execute(context, toolName);
    }

    private void createDefaultResume(String title, String content) {
        ResumeCreateRequest request = new ResumeCreateRequest();
        request.setTitle(title);
        request.setContent(content);
        var created = resumeService.createResume(request);
        resumeService.setDefaultResume(created.getId());
    }

    private void insertMatch(long userId, String jobTitle) {
        OffsetDateTime now = OffsetDateTime.now();
        JobMatchEntity entity = new JobMatchEntity();
        entity.setUserId(userId);
        entity.setJobTitle(jobTitle);
        entity.setCompanyName("co");
        entity.setJdContent("jd");
        entity.setMatchScore(70);
        entity.setMatchLevel("MEDIUM");
        entity.setMatchedSkills(jobMatchJsonSupport.writeStringList(List.of("Java")));
        entity.setMissingSkills(jobMatchJsonSupport.writeStringList(List.of("Docker")));
        entity.setStrengths(jobMatchJsonSupport.writeStringList(List.of("s")));
        entity.setRisks(jobMatchJsonSupport.writeStringList(List.of("r")));
        entity.setSuggestions(jobMatchJsonSupport.writeStringList(List.of("u")));
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
