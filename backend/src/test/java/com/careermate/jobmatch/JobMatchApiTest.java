package com.careermate.jobmatch;

import com.careermate.mapper.JobMatchMapper;
import com.careermate.mapper.ResumeMapper;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.model.entity.JobMatchEntity;
import com.careermate.model.entity.ResumeEntity;
import com.careermate.jobmatch.service.JobMatchService;
import com.careermate.resume.service.ResumeService;
import com.careermate.security.CurrentUser;
import com.careermate.security.CurrentUserContext;
import com.careermate.testsupport.TestUserSupport;
import com.careermate.testsupport.TestUsers;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class JobMatchApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    void analyzeFailsWithoutDefaultResume() throws Exception {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        String body = objectMapper.writeValueAsString(Map.of(
                "jobTitle", "test_java_backend",
                "companyName", "test_company",
                "jdContent", "Java, Redis"
        ));

        mockMvc.perform(post("/api/job-matches/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message")
                        .value("岗位匹配需要读取你的简历来评估契合度，请先到「我的简历」上传并设为默认简历后再试。"));
    }

    @Test
    void analyzeSaveListGetDeleteAndIsolation() throws Exception {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        insertDefaultResume(TestUsers.USER_A, "test_match_resume", "Java, Spring Boot, PostgreSQL, Redis");

        String analyzeBody = objectMapper.writeValueAsString(Map.of(
                "jobTitle", "test_java_backend_engineer",
                "companyName", "test_company",
                "jdContent", "Java, Spring Boot, Redis, Elasticsearch, Docker"
        ));

        String analyzeJson = mockMvc.perform(post("/api/job-matches/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analyzeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.matchScore").isNumber())
                .andExpect(jsonPath("$.data.matchedSkills").isArray())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long matchId = objectMapper.readTree(analyzeJson).path("data").path("id").asLong();

        mockMvc.perform(get("/api/job-matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].jobTitle").value("test_java_backend_engineer"));

        mockMvc.perform(get("/api/job-matches/" + matchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jdContent").exists())
                .andExpect(jsonPath("$.data.strengths").isArray());

        loginAs(TestUsers.USER_B, TestUsers.USER_B_NAME);
        mockMvc.perform(get("/api/job-matches/" + matchId))
                .andExpect(status().isNotFound());

        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        mockMvc.perform(delete("/api/job-matches/" + matchId))
                .andExpect(status().isOk());

        JobMatchEntity deleted = jobMatchMapper.selectById(matchId);
        assertEquals(JobMatchService.STATUS_DELETED, deleted.getStatus());

        mockMvc.perform(get("/api/job-matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private void insertDefaultResume(Long userId, String title, String content) {
        ResumeEntity entity = new ResumeEntity();
        entity.setUserId(userId);
        entity.setTitle(title);
        entity.setContent(content);
        entity.setSourceType(ResumeService.SOURCE_TYPE_TEXT);
        entity.setIsDefault(true);
        entity.setStatus(ResumeService.STATUS_ACTIVE);
        entity.setCreatedAt(java.time.OffsetDateTime.now());
        entity.setUpdatedAt(java.time.OffsetDateTime.now());
        resumeMapper.insert(entity);
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
