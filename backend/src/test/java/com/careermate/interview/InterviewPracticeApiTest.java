package com.careermate.interview;

import com.careermate.jobmatch.JobMatchJsonSupport;
import com.careermate.jobmatch.service.JobMatchService;
import com.careermate.mapper.InterviewQuestionMapper;
import com.careermate.mapper.InterviewSessionMapper;
import com.careermate.mapper.JobMatchMapper;
import com.careermate.mapper.ResumeMapper;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.model.entity.JobMatchEntity;
import com.careermate.model.entity.ResumeEntity;
import com.careermate.resume.service.ResumeService;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class InterviewPracticeApiTest {

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
    void createFailsWithoutDefaultResume() throws Exception {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        mockMvc.perform(post("/api/interview-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请先创建并设置默认简历后再开始面试训练"));
    }

    @Test
    void createWithFiveQuestionsAndGapFromJobMatch() throws Exception {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        insertDefaultResume(TestUsers.USER_A, "test_interview_resume", "Java, Spring Boot, Redis");
        insertJobMatch(TestUsers.USER_A, "test_interview_job");

        String json = mockMvc.perform(post("/api/interview-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "test_session"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalQuestions").value(5))
                .andExpect(jsonPath("$.data.questions.length()").value(5))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(json);
        JsonNode questions = root.path("data").path("questions");
        boolean hasGapWithElasticsearch = false;
        for (JsonNode q : questions) {
            if ("GAP".equals(q.path("questionType").asText())) {
                String text = q.path("questionText").asText();
                if (text.contains("Elasticsearch") || text.contains("Docker")) {
                    hasGapWithElasticsearch = true;
                }
            }
        }
        assertTrue(hasGapWithElasticsearch, "GAP question should mention missing skills");
    }

    @Test
    void submitAnswerCompleteDeleteAndIsolation() throws Exception {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        insertDefaultResume(TestUsers.USER_A, "test_interview_resume_a", "Java, Spring Boot");

        String createJson = mockMvc.perform(post("/api/interview-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long sessionId = objectMapper.readTree(createJson).path("data").path("id").asLong();
        long questionId = objectMapper.readTree(createJson).path("data").path("questions").get(0).path("id").asLong();

        String answerBody = objectMapper.writeValueAsString(Map.of(
                "answerText",
                "我在项目中使用 Java 和 Spring Boot 负责核心模块开发，通过 Redis 缓存优化接口性能，QPS 提升 30%。"
        ));

        mockMvc.perform(post("/api/interview-sessions/" + sessionId + "/questions/" + questionId + "/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ANSWERED"))
                .andExpect(jsonPath("$.data.score").isNumber())
                .andExpect(jsonPath("$.data.feedback").isNotEmpty());

        mockMvc.perform(get("/api/interview-sessions/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answeredQuestions").value(1))
                .andExpect(jsonPath("$.data.averageScore").isNumber());

        mockMvc.perform(post("/api/interview-sessions/" + sessionId + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.summary").isNotEmpty());

        mockMvc.perform(delete("/api/interview-sessions/" + sessionId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/interview-sessions/" + sessionId))
                .andExpect(status().isNotFound());

        loginAs(TestUsers.USER_B, TestUsers.USER_B_NAME);
        mockMvc.perform(get("/api/interview-sessions/" + sessionId))
                .andExpect(status().isNotFound());
    }

    @Test
    void listExcludesDeletedSessions() throws Exception {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        insertDefaultResume(TestUsers.USER_A, "test_interview_list", "Java");

        String createJson = mockMvc.perform(post("/api/interview-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long sessionId = objectMapper.readTree(createJson).path("data").path("id").asLong();

        mockMvc.perform(delete("/api/interview-sessions/" + sessionId))
                .andExpect(status().isOk());

        String listJson = mockMvc.perform(get("/api/interview-sessions"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode list = objectMapper.readTree(listJson).path("data");
        for (JsonNode item : list) {
            assertFalse(sessionId == item.path("id").asLong());
        }
    }

    private void insertDefaultResume(Long userId, String title, String content) {
        OffsetDateTime now = OffsetDateTime.now();
        ResumeEntity entity = new ResumeEntity();
        entity.setUserId(userId);
        entity.setTitle(title);
        entity.setContent(content);
        entity.setSourceType(ResumeService.SOURCE_TYPE_TEXT);
        entity.setIsDefault(true);
        entity.setStatus(ResumeService.STATUS_ACTIVE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        resumeMapper.insert(entity);
    }

    private void insertJobMatch(Long userId, String jobTitle) {
        OffsetDateTime now = OffsetDateTime.now();
        JobMatchEntity entity = new JobMatchEntity();
        entity.setUserId(userId);
        entity.setJobTitle(jobTitle);
        entity.setCompanyName("test_co");
        entity.setJdContent("Java, Spring Boot, Redis, Elasticsearch, Docker");
        entity.setMatchScore(65);
        entity.setMatchLevel("MEDIUM");
        entity.setMatchedSkills(jobMatchJsonSupport.writeStringList(List.of("Java", "Spring Boot", "Redis")));
        entity.setMissingSkills(jobMatchJsonSupport.writeStringList(List.of("Elasticsearch", "Docker")));
        entity.setStrengths(jobMatchJsonSupport.writeStringList(List.of("s1")));
        entity.setRisks(jobMatchJsonSupport.writeStringList(List.of("r1")));
        entity.setSuggestions(jobMatchJsonSupport.writeStringList(List.of("u1")));
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
