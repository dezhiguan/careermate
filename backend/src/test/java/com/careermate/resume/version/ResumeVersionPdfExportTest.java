package com.careermate.resume.version;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.artifact.service.AgentArtifactService;
import com.careermate.common.exception.BizException;
import com.careermate.mapper.ResumeVersionMapper;
import com.careermate.model.entity.ResumeVersionEntity;
import com.careermate.resume.version.export.ResumeVersionDocxRenderer;
import com.careermate.resume.version.export.ResumeVersionPdfRenderer;
import com.careermate.resume.version.service.impl.ResumeVersionServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeVersionPdfExportTest {

    @Mock
    private ResumeVersionMapper resumeVersionMapper;
    @Mock
    private AgentArtifactService agentArtifactService;

    private ResumeVersionPdfRenderer pdfRenderer;
    private ResumeVersionDocxRenderer docxRenderer;
    private ResumeVersionServiceImpl service;

    @BeforeEach
    void setUp() {
        pdfRenderer = new ResumeVersionPdfRenderer();
        docxRenderer = new ResumeVersionDocxRenderer();
        service = new ResumeVersionServiceImpl(
                resumeVersionMapper, new ObjectMapper(), pdfRenderer, docxRenderer, agentArtifactService,
                new com.careermate.resume.version.verify.ResumeFactVerifier(),
                org.mockito.Mockito.mock(com.careermate.mapper.JobApplicationMapper.class)
        );
    }

    @Test
    void exportPdfReturnsPdfStream() throws Exception {
        ResumeVersionEntity entity = sampleEntity("ver-ok", "# 张三\n\n## 工作经历\n熟悉 **Java** 开发");
        when(resumeVersionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        MockHttpServletResponse response = new MockHttpServletResponse();
        service.exportPdf(1L, "ver-ok", response);

        assertEquals("application/pdf", response.getContentType());
        assertTrue(response.getContentAsByteArray().length > 0);
        assertTrue(response.getHeader("Content-Disposition").contains("attachment"));
    }

    @Test
    void exportPdfNullUserThrows401() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        BizException ex = assertThrows(BizException.class, () -> service.exportPdf(null, "any", response));
        assertEquals(401, ex.getCode());
    }

    @Test
    void exportPdfVersionNotFoundThrows404() {
        when(resumeVersionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        MockHttpServletResponse response = new MockHttpServletResponse();
        BizException ex = assertThrows(BizException.class, () -> service.exportPdf(1L, "missing", response));
        assertEquals(404, ex.getCode());
    }

    @Test
    void exportPdfOtherUserThrows404() {
        ResumeVersionEntity entity = sampleEntity("ver-other", "# test");
        entity.setUserId(2L);
        when(resumeVersionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        MockHttpServletResponse response = new MockHttpServletResponse();
        // BUG-21：越权与不存在统一 404，消除存在性侧信道
        BizException ex = assertThrows(BizException.class, () -> service.exportPdf(1L, "ver-other", response));
        assertEquals(404, ex.getCode());
    }

    @Test
    void exportPdfChineseContentSucceeds() throws Exception {
        ResumeVersionEntity entity = sampleEntity("ver-cn", "# 个人简历\n\n## 工作经历\n负责后端系统开发，熟悉 Spring Boot。");
        when(resumeVersionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        MockHttpServletResponse response = new MockHttpServletResponse();
        service.exportPdf(1L, "ver-cn", response);

        assertEquals("application/pdf", response.getContentType());
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void exportDocxReturnsWordStream() throws Exception {
        ResumeVersionEntity entity = sampleEntity("ver-docx", "# 张三\n\n- Java\n- Spring Boot");
        when(resumeVersionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        MockHttpServletResponse response = new MockHttpServletResponse();
        service.exportDocx(1L, "ver-docx", response);

        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", response.getContentType());
        assertTrue(response.getContentAsByteArray().length > 0);
        assertTrue(response.getHeader("Content-Disposition").contains(".docx"));
    }

    @Test
    void exportPdfRendererFailureThrows500() throws Exception {
        ResumeVersionEntity entity = sampleEntity("ver-fail", "# test");
        when(resumeVersionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        ResumeVersionPdfRenderer failingRenderer = org.mockito.Mockito.mock(ResumeVersionPdfRenderer.class);
        doAnswer(invocation -> {
            throw new RuntimeException("render failed");
        }).when(failingRenderer).render(eq("# test"), any());
        ResumeVersionServiceImpl failingService = new ResumeVersionServiceImpl(
                resumeVersionMapper, new ObjectMapper(), failingRenderer, docxRenderer, agentArtifactService,
                new com.careermate.resume.version.verify.ResumeFactVerifier(),
                org.mockito.Mockito.mock(com.careermate.mapper.JobApplicationMapper.class)
        );

        MockHttpServletResponse response = new MockHttpServletResponse();
        BizException ex = assertThrows(BizException.class, () -> failingService.exportPdf(1L, "ver-fail", response));
        assertEquals(500, ex.getCode());
    }

    private static ResumeVersionEntity sampleEntity(String versionId, String markdown) {
        ResumeVersionEntity entity = new ResumeVersionEntity();
        entity.setVersionId(versionId);
        entity.setUserId(1L);
        entity.setVersionName("测试版");
        entity.setContentMarkdown(markdown);
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }
}
