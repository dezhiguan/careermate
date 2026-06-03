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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class CareerTaskApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    void createListUpdateDoneAndDelete() throws Exception {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        Map<String, Object> body = Map.of(
                "title", "补充 Java 后端项目量化指标",
                "description", "写清楚 QPS 与缓存命中率",
                "category", "RESUME",
                "priority", "HIGH",
                "dueDate", "2026-06-10"
        );

        String createJson = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("TODO"))
                .andExpect(jsonPath("$.data.title").value("补充 Java 后端项目量化指标"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long taskId = objectMapper.readTree(createJson).path("data").path("id").asLong();

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(taskId))
                .andExpect(jsonPath("$.data[0].status").value("TODO"));

        mockMvc.perform(put("/api/tasks/" + taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("priority", "LOW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priority").value("LOW"));

        mockMvc.perform(put("/api/tasks/" + taskId + "/done"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.completedAt").exists());

        mockMvc.perform(put("/api/tasks/" + taskId + "/todo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("TODO"));

        mockMvc.perform(delete("/api/tasks/" + taskId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
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
