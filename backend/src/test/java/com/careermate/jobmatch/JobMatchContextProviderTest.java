package com.careermate.jobmatch;

import com.careermate.mapper.JobMatchMapper;
import com.careermate.mapper.ResumeMapper;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.model.entity.JobMatchEntity;
import com.careermate.security.CurrentUser;
import com.careermate.security.CurrentUserContext;
import com.careermate.testsupport.TestUserSupport;
import com.careermate.testsupport.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class JobMatchContextProviderTest {

    @Autowired
    private JobMatchContextProvider jobMatchContextProvider;

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
    void returnsEmptyWhenNoMatch() {
        JobMatchContext ctx = jobMatchContextProvider.getLatestJobMatchContext(TestUsers.USER_A);
        assertFalse(ctx.isAvailable());
        assertEquals("当前用户暂无岗位匹配记录。", ctx.getContextText());
    }

    @Test
    void returnsLatestMatchForUser() {
        insertMatch(TestUsers.USER_A, "test_job_older", 60, "MEDIUM", 10);
        insertMatch(TestUsers.USER_A, "test_job_latest", 72, "HIGH", 0);

        JobMatchContext ctx = jobMatchContextProvider.getLatestJobMatchContext(TestUsers.USER_A);
        assertTrue(ctx.isAvailable());
        assertEquals("test_job_latest", ctx.getJobTitle());
        assertEquals(72, ctx.getMatchScore());
        assertTrue(ctx.getContextText().contains("最近岗位匹配结果"));
        assertTrue(ctx.getContextText().contains("Java"));
        assertTrue(ctx.getContextText().contains("Elasticsearch"));
    }

    @Test
    void userIsolation() {
        insertMatch(TestUsers.USER_A, "test_job_user_a", 80, "HIGH", 0);
        insertMatch(TestUsers.USER_B, "test_job_user_b", 55, "LOW", 0);

        JobMatchContext ctxA = jobMatchContextProvider.getLatestJobMatchContext(TestUsers.USER_A);
        JobMatchContext ctxB = jobMatchContextProvider.getLatestJobMatchContext(TestUsers.USER_B);

        assertEquals("test_job_user_a", ctxA.getJobTitle());
        assertEquals("test_job_user_b", ctxB.getJobTitle());
    }

    private void insertMatch(Long userId, String jobTitle, int score, String level, int ageMinutes) {
        OffsetDateTime now = OffsetDateTime.now().minusMinutes(ageMinutes);
        JobMatchEntity entity = new JobMatchEntity();
        entity.setUserId(userId);
        entity.setResumeId(null);
        entity.setJobTitle(jobTitle);
        entity.setCompanyName("test_company");
        entity.setJdContent("Java, Spring Boot, Redis, Elasticsearch, Docker requirement text");
        entity.setMatchScore(score);
        entity.setMatchLevel(level);
        entity.setMatchedSkills(jobMatchJsonSupport.writeStringList(java.util.List.of("Java", "Spring Boot", "Redis")));
        entity.setMissingSkills(jobMatchJsonSupport.writeStringList(java.util.List.of("Elasticsearch", "Docker")));
        entity.setStrengths(jobMatchJsonSupport.writeStringList(java.util.List.of("test strength")));
        entity.setRisks(jobMatchJsonSupport.writeStringList(java.util.List.of("test risk")));
        entity.setSuggestions(jobMatchJsonSupport.writeStringList(java.util.List.of("test suggestion")));
        entity.setAnalysisSummary("test summary");
        entity.setStatus(JobMatchService.STATUS_ACTIVE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        jobMatchMapper.insert(entity);
    }
}
