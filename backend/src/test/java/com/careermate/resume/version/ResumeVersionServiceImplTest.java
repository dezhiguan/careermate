package com.careermate.resume.version;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.artifact.ArtifactConstants;
import com.careermate.artifact.dto.CreateAgentArtifactCommand;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeVersionServiceImplTest {

    @Mock
    private ResumeVersionMapper resumeVersionMapper;
    @Mock
    private ResumeVersionPdfRenderer pdfRenderer;
    @Mock
    private AgentArtifactService agentArtifactService;

    private ResumeVersionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ResumeVersionServiceImpl(
                resumeVersionMapper,
                new ObjectMapper(),
                pdfRenderer,
                new ResumeVersionDocxRenderer(),
                agentArtifactService
        );
    }

    @Test
    void createPersistsAllFields() {
        service.createVersion(
                1L,
                "WS-abc",
                99L,
                "doc-1",
                "腾讯 算法工程师",
                "腾讯 - 算法工程师",
                "# 简历\n内容",
                List.of(Map.of("field", "技能", "change", "对齐 JD"))
        );

        ArgumentCaptor<ResumeVersionEntity> captor = ArgumentCaptor.forClass(ResumeVersionEntity.class);
        verify(resumeVersionMapper).insert(captor.capture());
        ResumeVersionEntity saved = captor.getValue();
        assertEquals(1L, saved.getUserId());
        assertEquals("WS-abc", saved.getSessionId());
        assertEquals(99L, saved.getSourceResumeId());
        assertEquals("doc-1", saved.getTargetJdId());
        assertNotNull(saved.getVersionId());
        assertEquals("# 简历\n内容", saved.getContentMarkdown());
        assertNotNull(saved.getOptimizationNotes());
    }

    @Test
    void listOrderedByCreatedAtDesc() {
        ResumeVersionEntity older = entity("v-old", LocalDateTime.of(2026, 6, 1, 10, 0));
        ResumeVersionEntity newer = entity("v-new", LocalDateTime.of(2026, 6, 2, 10, 0));
        when(resumeVersionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(newer, older));

        var list = service.listBySession(1L, "WS-abc");

        assertEquals(2, list.size());
        assertEquals("v-new", list.get(0).versionId());
    }

    @Test
    void updateVersionPersistsNameAndContent() {
        ResumeVersionEntity entity = entity("v-1", LocalDateTime.now());
        entity.setId(10L);
        entity.setUserId(1L);
        when(resumeVersionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        var updated = service.updateVersion(1L, "v-1", "新标题", "# 新内容");

        assertEquals("新标题", updated.versionName());
        assertEquals("# 新内容", updated.contentMarkdown());
        verify(resumeVersionMapper).updateById(entity);
        assertEquals("新标题", entity.getVersionName());
        assertEquals("# 新内容", entity.getContentMarkdown());
    }

    @Test
    void createVersionRegistersResumeArtifact() {
        service.createVersion(
                1L,
                "WS-session",
                99L,
                "doc-1",
                "腾讯 算法工程师",
                "腾讯 - 算法工程师",
                "# 简历\n内容",
                List.of()
        );

        ArgumentCaptor<ResumeVersionEntity> versionCaptor = ArgumentCaptor.forClass(ResumeVersionEntity.class);
        verify(resumeVersionMapper).insert(versionCaptor.capture());
        String versionId = versionCaptor.getValue().getVersionId();

        ArgumentCaptor<CreateAgentArtifactCommand> captor = ArgumentCaptor.forClass(CreateAgentArtifactCommand.class);
        verify(agentArtifactService).create(captor.capture());
        CreateAgentArtifactCommand artifact = captor.getValue();
        assertEquals(ArtifactConstants.TYPE_RESUME_VERSION, artifact.artifactType());
        assertEquals(ArtifactConstants.REF_RESUME_VERSION, artifact.refType());
        assertEquals(versionId, artifact.refId());
        assertEquals("WS-session", artifact.sessionId());
        assertEquals("腾讯 - 算法工程师", artifact.title());
    }

    @Test
    void tenantIsolationForbidden() {
        ResumeVersionEntity entity = entity("v-1", LocalDateTime.now());
        entity.setUserId(1L);
        when(resumeVersionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        assertThrows(BizException.class, () -> service.getVersion(2L, "v-1"));
    }

    private static ResumeVersionEntity entity(String versionId, LocalDateTime createdAt) {
        ResumeVersionEntity entity = new ResumeVersionEntity();
        entity.setVersionId(versionId);
        entity.setUserId(1L);
        entity.setVersionName("测试版");
        entity.setTargetJdLabel("腾讯 算法工程师");
        entity.setContentMarkdown("md");
        entity.setCreatedAt(createdAt);
        return entity;
    }
}
