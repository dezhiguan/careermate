package com.careermate.resume;

import com.careermate.auth.dto.RegisterRequest;
import com.careermate.mapper.ResumeMapper;
import com.careermate.mapper.ResumeVersionMapper;
import com.careermate.model.entity.ResumeEntity;
import com.careermate.model.entity.ResumeVersionEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = true)
class ResumeDownloadSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ResumeVersionMapper resumeVersionMapper;

    @Autowired
    private ResumeMapper resumeMapper;

    @Test
    void resumeAndVersionDownloadsRequireBearerAndReturnBinary() throws Exception {
        String username = "test_resume_download_" + System.nanoTime();
        String token = registerAndGetToken(username);

        String createBody = objectMapper.writeValueAsString(Map.of(
                "title", "下载测试简历",
                "content", "# 下载测试简历\n\n熟悉 Java、Spring Boot。"
        ));
        MvcResult createResult = mockMvc.perform(post("/api/resumes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn();
        long resumeId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asLong();

        mockMvc.perform(get("/api/resumes/" + resumeId + "/export/pdf"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/resumes/" + resumeId + "/export/pdf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")));

        mockMvc.perform(get("/api/resumes/" + resumeId + "/export/docx")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(".docx")));

        String versionId = insertResumeVersion(resumeId, username);

        mockMvc.perform(get("/api/resume-version/" + versionId + "/export/pdf"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/resume-version/" + versionId + "/export/pdf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")));

        mockMvc.perform(get("/api/resume-version/" + versionId + "/export/docx")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(".docx")));
    }

    private String registerAndGetToken(String username) throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername(username);
        registerRequest.setPassword("Test123456!");
        registerRequest.setEmail(username + "@careermate.test");

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode registerJson = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        return registerJson.path("data").path("token").asText();
    }

    private String insertResumeVersion(long resumeId, String username) {
        ResumeEntity resume = resumeMapper.selectById(resumeId);
        if (resume == null || resume.getUserId() == null) {
            throw new IllegalStateException("测试简历用户不存在: " + username);
        }

        String versionId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        ResumeVersionEntity entity = new ResumeVersionEntity();
        entity.setVersionId(versionId);
        entity.setUserId(resume.getUserId());
        entity.setTenantId(1L);
        entity.setSessionId("test-download");
        entity.setSourceResumeId(resumeId);
        entity.setTargetJdId("test-jd");
        entity.setTargetJdLabel("测试 JD");
        entity.setVersionName("定制下载测试");
        entity.setContentMarkdown("# 定制下载测试\n\n- Java\n- Spring Boot");
        entity.setOptimizationNotes("[]");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        resumeVersionMapper.insert(entity);
        return versionId;
    }
}
