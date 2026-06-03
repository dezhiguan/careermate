package com.careermate.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.mapper.CareerProfileMapper;
import com.careermate.mapper.CareerTaskMapper;
import com.careermate.mapper.JobMatchMapper;
import com.careermate.mapper.ResumeMapper;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.model.entity.CareerProfileEntity;
import com.careermate.model.entity.CareerTaskEntity;
import com.careermate.security.CurrentUser;
import com.careermate.security.CurrentUserContext;
import com.careermate.task.CareerTaskConstants;
import com.careermate.testsupport.TestUserSupport;
import com.careermate.testsupport.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class DashboardTaskOverviewTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CareerTaskMapper careerTaskMapper;

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
        careerTaskMapper.delete(new LambdaQueryWrapper<CareerTaskEntity>()
                .in(CareerTaskEntity::getUserId, java.util.List.of(TestUsers.USER_A, TestUsers.USER_B)));
        careerProfileMapper.delete(new LambdaQueryWrapper<CareerProfileEntity>()
                .in(CareerProfileEntity::getUserId, java.util.List.of(TestUsers.USER_A, TestUsers.USER_B)));
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void overviewIncludesRecentTodoTasksOnly() throws Exception {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        insertTask("待办高优", CareerTaskConstants.PRIORITY_HIGH, CareerTaskConstants.STATUS_TODO);
        insertTask("已完成任务", CareerTaskConstants.PRIORITY_HIGH, CareerTaskConstants.STATUS_DONE);
        insertTask("待办低优", CareerTaskConstants.PRIORITY_LOW, CareerTaskConstants.STATUS_TODO);

        mockMvc.perform(get("/api/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resumeStats").exists())
                .andExpect(jsonPath("$.data.tasks.length()").value(2))
                .andExpect(jsonPath("$.data.tasks[0].title").value("待办高优"))
                .andExpect(jsonPath("$.data.tasks[0].status").value("TODO"))
                .andExpect(jsonPath("$.data.tasks[1].title").value("待办低优"));
    }

    @Test
    void overviewTasksAreUserIsolated() throws Exception {
        insertTaskForUser(TestUsers.USER_B, "用户B任务", CareerTaskConstants.STATUS_TODO);
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);

        mockMvc.perform(get("/api/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tasks.length()").value(0));
    }

    private void insertTask(String title, String priority, String status) {
        insertTaskForUser(TestUsers.USER_A, title, status, priority);
    }

    private void insertTaskForUser(long userId, String title, String status) {
        insertTaskForUser(userId, title, status, CareerTaskConstants.PRIORITY_MEDIUM);
    }

    private void insertTaskForUser(long userId, String title, String status, String priority) {
        CareerTaskEntity entity = new CareerTaskEntity();
        entity.setUserId(userId);
        entity.setTitle(title);
        entity.setCategory(CareerTaskConstants.CATEGORY_RESUME);
        entity.setPriority(priority);
        entity.setStatus(status);
        entity.setSource(CareerTaskConstants.SOURCE_MANUAL);
        entity.setDueDate(LocalDate.of(2026, 6, 10));
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        if (CareerTaskConstants.STATUS_DONE.equals(status)) {
            entity.setCompletedAt(now);
        }
        careerTaskMapper.insert(entity);
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
