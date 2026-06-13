package com.careermate.profile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.mapper.CareerProfileMapper;
import com.careermate.mapper.JobMatchMapper;
import com.careermate.mapper.ResumeMapper;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.model.entity.CareerProfileEntity;
import com.careermate.profile.service.CareerProfileAutoUpdateService;
import com.careermate.testsupport.TestUserSupport;
import com.careermate.testsupport.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class CareerProfileAutoUpdateTest {

    @Autowired
    private CareerProfileAutoUpdateService careerProfileAutoUpdateService;

    @Autowired
    private CareerProfileTargetRoleExtractor targetRoleExtractor;

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
    void extractsTargetRoleFromGoalMessage() {
        assertTrue(targetRoleExtractor.extract("我的目标是 Java 后端开发岗位").isPresent());
        assertEquals(
                "Java 后端开发",
                targetRoleExtractor.extract("我的目标是 Java 后端开发岗位").orElseThrow()
        );
    }

    @Test
    void autoUpdatesProfileTargetRole() {
        CareerProfileUpdateResult result = careerProfileAutoUpdateService.tryAutoUpdateTargetRole(
                TestUsers.USER_A,
                "我的目标是 Java 后端开发岗位"
        );
        assertTrue(result.isUpdated());
        assertEquals("Java 后端开发", result.getTargetRole());

        CareerProfileEntity entity = careerProfileMapper.selectOne(
                new LambdaQueryWrapper<CareerProfileEntity>()
                        .eq(CareerProfileEntity::getUserId, TestUsers.USER_A)
        );
        assertEquals("Java 后端开发", entity.getTargetRole());
    }

    @Test
    void ignoresUnrelatedMessage() {
        CareerProfileUpdateResult result = careerProfileAutoUpdateService.tryAutoUpdateTargetRole(
                TestUsers.USER_A,
                "今天天气怎么样"
        );
        assertFalse(result.isUpdated());
    }
}
