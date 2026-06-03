package com.careermate.profile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.mapper.CareerProfileMapper;
import com.careermate.mapper.JobMatchMapper;
import com.careermate.mapper.ResumeMapper;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.model.entity.CareerProfileEntity;
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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class CareerProfileApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CareerProfileMapper careerProfileMapper;

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
        careerProfileMapper.delete(new LambdaQueryWrapper<CareerProfileEntity>()
                .in(CareerProfileEntity::getUserId, List.of(TestUsers.USER_A, TestUsers.USER_B)));
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void getReturnsEmptyObjectWhenMissing() throws Exception {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        mockMvc.perform(get("/api/profile/career"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.targetRole").isEmpty());
    }

    @Test
    void putAndGetCurrentUserProfile() throws Exception {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        Map<String, Object> body = Map.of(
                "targetRole", "Java 后端开发工程师",
                "targetCity", "杭州",
                "seniority", "3-5年",
                "workMode", "远程/双休",
                "skillKeywords", List.of("Java", "Spring Boot", "Redis"),
                "preferenceSummary", "偏后端工程化"
        );

        mockMvc.perform(put("/api/profile/career")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetRole").value("Java 后端开发工程师"))
                .andExpect(jsonPath("$.data.targetCity").value("杭州"))
                .andExpect(jsonPath("$.data.skillKeywords.length()").value(3));

        mockMvc.perform(get("/api/profile/career"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetRole").value("Java 后端开发工程师"));
    }

    @Test
    void userIsolationOnRead() throws Exception {
        careerProfileMapper.insert(buildEntity(TestUsers.USER_A, "用户A岗位"));

        loginAs(TestUsers.USER_B, TestUsers.USER_B_NAME);
        mockMvc.perform(get("/api/profile/career"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetRole").isEmpty());

        long countB = careerProfileMapper.selectCount(
                new LambdaQueryWrapper<CareerProfileEntity>()
                        .eq(CareerProfileEntity::getUserId, TestUsers.USER_B)
        );
        assertEquals(0, countB);
    }

    private CareerProfileEntity buildEntity(long userId, String targetRole) {
        CareerProfileEntity entity = new CareerProfileEntity();
        entity.setUserId(userId);
        entity.setTargetRole(targetRole);
        entity.setSkillKeywords("[]");
        entity.setSource("agent");
        entity.setCreatedAt(java.time.LocalDateTime.now());
        entity.setUpdatedAt(java.time.LocalDateTime.now());
        return entity;
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
