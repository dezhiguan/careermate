package com.careermate.task;

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

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class CareerTaskUserIsolationTest {

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
    void cannotAccessOtherUserTasks() throws Exception {
        CareerTaskEntity task = new CareerTaskEntity();
        task.setUserId(TestUsers.USER_B);
        task.setTitle("用户B任务");
        task.setCategory(CareerTaskConstants.CATEGORY_RESUME);
        task.setPriority(CareerTaskConstants.PRIORITY_HIGH);
        task.setStatus(CareerTaskConstants.STATUS_TODO);
        task.setSource(CareerTaskConstants.SOURCE_MANUAL);
        LocalDateTime now = LocalDateTime.now();
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        careerTaskMapper.insert(task);

        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(put("/api/tasks/" + task.getId() + "/done"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        mockMvc.perform(delete("/api/tasks/" + task.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
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
