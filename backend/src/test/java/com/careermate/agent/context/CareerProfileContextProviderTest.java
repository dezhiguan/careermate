package com.careermate.agent.context;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.jobmatch.JobMatchJsonSupport;
import com.careermate.mapper.CareerProfileMapper;
import com.careermate.mapper.JobMatchMapper;
import com.careermate.mapper.ResumeMapper;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.model.entity.CareerProfileEntity;
import com.careermate.profile.dto.CareerProfileUpsertRequest;
import com.careermate.profile.service.CareerProfileService;
import com.careermate.testsupport.TestUserSupport;
import com.careermate.testsupport.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class CareerProfileContextProviderTest {

    @Autowired
    private CareerProfileContextProvider careerProfileContextProvider;

    @Autowired
    private CareerProfileService careerProfileService;

    @Autowired
    private CareerProfileMapper careerProfileMapper;

    @Autowired
    private JobMatchJsonSupport jobMatchJsonSupport;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ResumeMapper resumeMapper;

    @Autowired
    private JobMatchMapper jobMatchMapper;

    @BeforeEach
    void setUp() {
        TestUserSupport.ensureTestUsers(userMapper, userProfileMapper, passwordEncoder);
        TestUserSupport.cleanupUserBusinessData(resumeMapper, jobMatchMapper);
        careerProfileMapper.delete(new LambdaQueryWrapper<CareerProfileEntity>()
                .in(CareerProfileEntity::getUserId, List.of(TestUsers.USER_A, TestUsers.USER_B)));
    }

    @Test
    void buildsProfileContextForCurrentUser() {
        CareerProfileUpsertRequest request = new CareerProfileUpsertRequest();
        request.setTargetRole("Java 后端开发工程师");
        request.setTargetCity("杭州");
        request.setSkillKeywords(List.of("Java", "Spring Boot"));
        careerProfileService.upsertProfile(TestUsers.USER_A, request, "manual");

        CareerProfileEntity entity = careerProfileMapper.selectOne(
                new LambdaQueryWrapper<CareerProfileEntity>()
                        .eq(CareerProfileEntity::getUserId, TestUsers.USER_A)
        );
        entity.setTargetSalaryRange("25-35K");
        entity.setWeaknessKeywords(jobMatchJsonSupport.writeStringList(
                List.of("Redis 缓存一致性", "JVM GC 调优")
        ));
        entity.setInterviewWeaknessSummary("Redis 缓存一致性、JVM GC 调优");
        entity.setUpdatedAt(LocalDateTime.now());
        careerProfileMapper.updateById(entity);

        CareerProfileContextResult result = careerProfileContextProvider.load(TestUsers.USER_A);
        assertTrue(result.isAvailable());
        assertTrue(result.getContextText().contains("【用户求职画像】"));
        assertTrue(result.getContextText().contains("目标岗位：Java 后端开发工程师"));
        assertTrue(result.getContextText().contains("目标城市：杭州"));
        assertTrue(result.getContextText().contains("期望薪资：25-35K"));
        assertTrue(result.getContextText().contains("技能关键词：Java, Spring Boot"));
        assertTrue(result.getContextText().contains("近期弱项：Redis 缓存一致性、JVM GC 调优"));
        assertEqualsWeaknessCount(result, 2);
    }

    @Test
    void omitsEmptyFieldsFromContext() {
        CareerProfileUpsertRequest request = new CareerProfileUpsertRequest();
        request.setTargetRole("Java 后端开发工程师");
        careerProfileService.upsertProfile(TestUsers.USER_A, request, "manual");

        CareerProfileContextResult result = careerProfileContextProvider.load(TestUsers.USER_A);
        assertTrue(result.isAvailable());
        assertFalse(result.getContextText().contains("期望薪资："));
        assertFalse(result.getContextText().contains("近期弱项："));
        assertFalse(result.getContextText().contains("偏好说明：\n"));
    }

    @Test
    void doesNotLeakFullSensitiveAnswerText() {
        CareerProfileUpsertRequest request = new CareerProfileUpsertRequest();
        request.setTargetRole("Java 后端开发工程师");
        careerProfileService.upsertProfile(TestUsers.USER_A, request, "manual");

        CareerProfileEntity entity = careerProfileMapper.selectOne(
                new LambdaQueryWrapper<CareerProfileEntity>()
                        .eq(CareerProfileEntity::getUserId, TestUsers.USER_A)
        );
        String secretAnswer = "我在项目中使用 Java 和 Spring Boot 负责核心模块开发，通过 Redis 缓存优化接口性能，QPS 提升 30%。";
        entity.setInterviewWeaknessSummary("Redis 缓存一致性");
        entity.setMemorySummary("关注后端平台型岗位");
        entity.setUpdatedAt(LocalDateTime.now());
        careerProfileMapper.updateById(entity);

        CareerProfileContextResult result = careerProfileContextProvider.load(TestUsers.USER_A);
        assertTrue(result.getContextText().contains("近期弱项：Redis 缓存一致性"));
        assertFalse(result.getContextText().contains(secretAnswer));
    }

    @Test
    void doesNotLoadOtherUserProfile() {
        CareerProfileUpsertRequest request = new CareerProfileUpsertRequest();
        request.setTargetRole("用户B岗位");
        careerProfileService.upsertProfile(TestUsers.USER_B, request, "manual");

        CareerProfileContextResult result = careerProfileContextProvider.load(TestUsers.USER_A);
        assertFalse(result.isAvailable());
    }

    private void assertEqualsWeaknessCount(CareerProfileContextResult result, int expected) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, result.getWeaknessCount());
    }
}
