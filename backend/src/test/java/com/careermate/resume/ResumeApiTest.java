package com.careermate.resume;

import com.careermate.mapper.ResumeMapper;
import com.careermate.model.entity.ResumeEntity;
import com.careermate.security.CurrentUser;
import com.careermate.security.CurrentUserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class ResumeApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ResumeMapper resumeMapper;

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
    void createListUpdateDefaultDeleteAndIsolation() throws Exception {
        loginAs(1L, "local-user");

        String createBody = objectMapper.writeValueAsString(Map.of(
                "title", "Java后端简历",
                "content", "三年 Java 开发经验，熟悉 Spring Boot。"
        ));

        String createJson = mockMvc.perform(post("/api/resumes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("Java后端简历"))
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
                "title", "Java后端简历-更新",
                "content", "更新后的正文内容。"
        ));
        mockMvc.perform(put("/api/resumes/" + resumeId).contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Java后端简历-更新"));

        loginAs(2L, "other-user");
        mockMvc.perform(get("/api/resumes/" + resumeId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        loginAs(1L, "local-user");
        String secondCreate = objectMapper.writeValueAsString(Map.of(
                "title", "第二份简历",
                "content", "第二份简历正文。"
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
