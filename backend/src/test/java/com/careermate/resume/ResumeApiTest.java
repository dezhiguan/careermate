package com.careermate.resume;

import com.careermate.mapper.JobMatchMapper;
import com.careermate.mapper.ResumeMapper;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.model.entity.ResumeEntity;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class ResumeApiTest {

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

    private void loginAs(long userId, String username) {
        CurrentUserContext.set(CurrentUser.builder()
                .userId(userId)
                .username(username)
                .role("USER")
                .authenticated(true)
                .build());
    }

    @Test
    void fileReUploadReplacesPreviousResumeInsteadOfAccumulating() throws Exception {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);

        org.springframework.mock.web.MockMultipartFile f1 = new org.springframework.mock.web.MockMultipartFile(
                "file", "resume1.md", "text/markdown",
                "第一份简历 三年 Java 经验".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/resumes/upload").file(f1).param("title", "简历一"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(true));

        org.springframework.mock.web.MockMultipartFile f2 = new org.springframework.mock.web.MockMultipartFile(
                "file", "resume2.md", "text/markdown",
                "第二份简历 五年 Go 经验".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/resumes/upload").file(f2).param("title", "简历二"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(true));

        // 修复验证：重新上传=替换 → 只剩 1 份 ACTIVE，且为最新那份、且为默认；旧份不再残留
        mockMvc.perform(get("/api/resumes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("简历二"))
                .andExpect(jsonPath("$.data[0].isDefault").value(true));
    }

    @Test
    void createListUpdateDefaultDeleteAndIsolation() throws Exception {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);

        String createBody = objectMapper.writeValueAsString(Map.of(
                "title", "test_java_resume",
                "content", "三年 Java 开发经验，熟悉 Spring Boot。"
        ));

        String createJson = mockMvc.perform(post("/api/resumes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("test_java_resume"))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long resumeId = objectMapper.readTree(createJson).path("data").path("id").asLong();

        mockMvc.perform(get("/api/resumes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].contentPreview").exists());

        String updateBody = objectMapper.writeValueAsString(Map.of(
                "title", "test_java_resume_updated",
                "content", "test 更新后的正文内容。"
        ));
        mockMvc.perform(put("/api/resumes/" + resumeId).contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("test_java_resume_updated"));

        loginAs(TestUsers.USER_B, TestUsers.USER_B_NAME);
        mockMvc.perform(get("/api/resumes/" + resumeId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        String secondCreate = objectMapper.writeValueAsString(Map.of(
                "title", "test_second_resume",
                "content", "test 第二份简历正文。"
        ));
        String secondJson = mockMvc.perform(post("/api/resumes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondCreate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long secondId = objectMapper.readTree(secondJson).path("data").path("id").asLong();

        mockMvc.perform(post("/api/resumes/" + secondId + "/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(true));

        ResumeEntity first = resumeMapper.selectById(resumeId);
        ResumeEntity second = resumeMapper.selectById(secondId);
        assertFalse(Boolean.TRUE.equals(first.getIsDefault()));
        assertTrue(Boolean.TRUE.equals(second.getIsDefault()));

        mockMvc.perform(delete("/api/resumes/" + secondId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ResumeEntity deleted = resumeMapper.selectById(secondId);
        assertEquals(ResumeService.STATUS_DELETED, deleted.getStatus());
        assertFalse(Boolean.TRUE.equals(deleted.getIsDefault()));

        ResumeEntity promoted = resumeMapper.selectById(resumeId);
        assertTrue(Boolean.TRUE.equals(promoted.getIsDefault()));

        mockMvc.perform(get("/api/resumes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }
}
