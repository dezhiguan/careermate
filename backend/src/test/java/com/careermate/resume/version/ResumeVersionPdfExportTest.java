package com.careermate.resume.version;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.common.exception.BizException;
import com.careermate.mapper.ResumeVersionMapper;
import com.careermate.model.entity.ResumeVersionEntity;
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

    private ResumeVersionPdfRenderer pdfRenderer;
    private ResumeVersionServiceImpl service;

    @BeforeEach
    void setUp() {
        pdfRenderer = new ResumeVersionPdfRenderer();
        service = new ResumeVersionServiceImpl(resumeVersionMapper, new ObjectMapper(), pdfRenderer);
    }

    @Test
    void exportPdfReturnsPdfStream() throws Exception {
        ResumeVersionEntity entity = sampleEntity("ver-ok", "# 张三\n\n## 工作经历\n熟悉 **Java** 开发");
        when(resumeVersionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        MockHttpServletResponse response = new MockHttpServletResponse();
        service.exportPdf("ver-ok", response);

        assertEquals("application/pdf", response.getContentType());
        assertTrue(response.getContentAsByteArray().length > 0);
        assertTrue(response.getHeader("Content-Disposition").contains("inline"));
    }

    @Test
    void exportPdfVersionNotFoundThrows404() {
        when(resumeVersionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        MockHttpServletResponse response = new MockHttpServletResponse();
        BizException ex = assertThrows(BizException.class, () -> service.exportPdf("missing", response));
        assertEquals(404, ex.getCode());
    }

    @Test
    void exportPdfChineseContentSucceeds() throws Exception {
        ResumeVersionEntity entity = sampleEntity("ver-cn", "# 个人简历\n\n## 工作经历\n负责后端系统开发，熟悉 Spring Boot。");
        when(resumeVersionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        MockHttpServletResponse response = new MockHttpServletResponse();
        service.exportPdf("ver-cn", response);

        assertEquals("application/pdf", response.getContentType());
        assertTrue(response.getContentAsByteArray().length > 0);
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
                resumeVersionMapper, new ObjectMapper(), failingRenderer
        );

        MockHttpServletResponse response = new MockHttpServletResponse();
        BizException ex = assertThrows(BizException.class, () -> failingService.exportPdf("ver-fail", response));
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
