package com.careermate.dashboard;

import com.careermate.interview.InterviewPracticeService;
import com.careermate.jobmatch.JobMatchJsonSupport;
import com.careermate.jobmatch.JobMatchService;
import com.careermate.mapper.InterviewQuestionMapper;
import com.careermate.mapper.InterviewSessionMapper;
import com.careermate.mapper.JobMatchMapper;
import com.careermate.mapper.ResumeMapper;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.model.entity.InterviewSessionEntity;
import com.careermate.model.entity.JobMatchEntity;
import com.careermate.model.entity.ResumeEntity;
import com.careermate.resume.ResumeService;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class DashboardApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ResumeMapper resumeMapper;

    @Autowired
    private JobMatchMapper jobMatchMapper;

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
    private JobMatchJsonSupport jobMatchJsonSupport;

    @BeforeEach
    void setUp() {
        TestUserSupport.ensureTestUsers(userMapper, userProfileMapper, passwordEncoder);
        TestUserSupport.cleanupUserBusinessData(
                resumeMapper, jobMatchMapper, interviewSessionMapper, interviewQuestionMapper);
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void emptyOverviewSuggestsCreateResume() throws Exception {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);

        mockMvc.perform(get("/api/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resumeStats.totalResumes").value(0))
                .andExpect(jsonPath("$.data.resumeStats.hasDefaultResume").value(false))
                .andExpect(jsonPath("$.data.suggestions[0].title").value("创建默认简历"))
                .andExpect(jsonPath("$.data.suggestions[0].route").value("/resume"));
    }

    @Test
    void withDefaultResumeSuggestsJobMatch() throws Exception {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        insertDefaultResume(TestUsers.USER_A, "test_dash_resume");

        mockMvc.perform(get("/api/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resumeStats.hasDefaultResume").value(true))
                .andExpect(jsonPath("$.data.jobMatchStats.totalMatches").value(0))
                .andExpect(jsonPath("$.data.suggestions[?(@.title=='录入岗位 JD')]").exists());
    }

    @Test
    void withJobMatchSuggestsInterview() throws Exception {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        insertDefaultResume(TestUsers.USER_A, "test_dash_resume");
        insertJobMatch(TestUsers.USER_A, "test_dash_job", 70);

        mockMvc.perform(get("/api/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobMatchStats.totalMatches").value(1))
                .andExpect(jsonPath("$.data.interviewStats.totalSessions").value(0))
                .andExpect(jsonPath("$.data.suggestions[?(@.title=='开始面试训练')]").exists());
    }

    @Test
    void recentActivitiesSortedDesc() throws Exception {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        OffsetDateTime base = OffsetDateTime.now();
        insertResume(TestUsers.USER_A, "test_old_resume", base.minusDays(2), false);
        insertDefaultResumeAt(TestUsers.USER_A, "test_new_resume", base.minusHours(1));
        insertJobMatchAt(TestUsers.USER_A, "test_job_recent", 80, base);

        String json = mockMvc.perform(get("/api/dashboard/overview"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode activities = objectMapper.readTree(json).path("data").path("recentActivities");
        assertTrue(activities.size() >= 2);
        OffsetDateTime prev = null;
        for (JsonNode node : activities) {
            OffsetDateTime at = OffsetDateTime.parse(node.path("occurredAt").asText());
            if (prev != null) {
                assertTrue(!at.isAfter(prev), "activities should be DESC by occurredAt");
            }
            prev = at;
        }
    }

    @Test
    void userIsolation() throws Exception {
        insertDefaultResume(TestUsers.USER_B, "test_dash_user_b_resume");
        insertJobMatch(TestUsers.USER_B, "test_dash_user_b_job", 90);

        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        mockMvc.perform(get("/api/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobMatchStats.totalMatches").value(0))
                .andExpect(jsonPath("$.data.resumeStats.totalResumes").value(0));
    }

    private void insertDefaultResume(Long userId, String title) {
        insertDefaultResumeAt(userId, title, OffsetDateTime.now());
    }

    private void insertDefaultResumeAt(Long userId, String title, OffsetDateTime now) {
        ResumeEntity entity = new ResumeEntity();
        entity.setUserId(userId);
        entity.setTitle(title);
        entity.setContent("Java, Spring Boot");
        entity.setSourceType(ResumeService.SOURCE_TYPE_TEXT);
        entity.setIsDefault(true);
        entity.setStatus(ResumeService.STATUS_ACTIVE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        resumeMapper.insert(entity);
    }

    private void insertResume(Long userId, String title, OffsetDateTime updatedAt, boolean isDefault) {
        ResumeEntity entity = new ResumeEntity();
        entity.setUserId(userId);
        entity.setTitle(title);
        entity.setContent("content");
        entity.setSourceType(ResumeService.SOURCE_TYPE_TEXT);
        entity.setIsDefault(isDefault);
        entity.setStatus(ResumeService.STATUS_ACTIVE);
        entity.setCreatedAt(updatedAt);
        entity.setUpdatedAt(updatedAt);
        resumeMapper.insert(entity);
    }

    private void insertJobMatch(Long userId, String jobTitle, int score) {
        insertJobMatchAt(userId, jobTitle, score, OffsetDateTime.now());
    }

    private void insertJobMatchAt(Long userId, String jobTitle, int score, OffsetDateTime now) {
        JobMatchEntity entity = new JobMatchEntity();
        entity.setUserId(userId);
        entity.setJobTitle(jobTitle);
        entity.setCompanyName("co");
        entity.setJdContent("Java");
        entity.setMatchScore(score);
        entity.setMatchLevel(score >= 75 ? "HIGH" : score >= 60 ? "MEDIUM" : "LOW");
        entity.setMatchedSkills(jobMatchJsonSupport.writeStringList(List.of("Java")));
        entity.setMissingSkills(jobMatchJsonSupport.writeStringList(List.of()));
        entity.setStrengths(jobMatchJsonSupport.writeStringList(List.of()));
        entity.setRisks(jobMatchJsonSupport.writeStringList(List.of()));
        entity.setSuggestions(jobMatchJsonSupport.writeStringList(List.of()));
        entity.setAnalysisSummary("s");
        entity.setStatus(JobMatchService.STATUS_ACTIVE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        jobMatchMapper.insert(entity);
    }

    private void insertInterviewSession(Long userId, String title, String status, OffsetDateTime now) {
        InterviewSessionEntity entity = new InterviewSessionEntity();
        entity.setUserId(userId);
        entity.setTitle(title);
        entity.setStatus(status);
        entity.setTotalQuestions(5);
        entity.setAnsweredQuestions(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        interviewSessionMapper.insert(entity);
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
