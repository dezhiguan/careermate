package com.careermate.agent.context;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.mapper.CareerProfileMapper;
import com.careermate.mapper.JobMatchMapper;
import com.careermate.mapper.ResumeMapper;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.model.entity.CareerProfileEntity;
import com.careermate.profile.CareerProfileService;
import com.careermate.profile.dto.CareerProfileUpsertRequest;
import com.careermate.testsupport.TestUserSupport;
import com.careermate.testsupport.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

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

        CareerProfileContextResult result = careerProfileContextProvider.load(TestUsers.USER_A);
        assertTrue(result.isAvailable());
        assertTrue(result.getContextText().contains("【用户求职画像】"));
        assertTrue(result.getContextText().contains("目标岗位：Java 后端开发工程师"));
        assertTrue(result.getContextText().contains("技能关键词：Java, Spring Boot"));
    }

    @Test
    void doesNotLoadOtherUserProfile() {
        CareerProfileUpsertRequest request = new CareerProfileUpsertRequest();
        request.setTargetRole("用户B岗位");
        careerProfileService.upsertProfile(TestUsers.USER_B, request, "manual");

        CareerProfileContextResult result = careerProfileContextProvider.load(TestUsers.USER_A);
        assertFalse(result.isAvailable());
    }
}
