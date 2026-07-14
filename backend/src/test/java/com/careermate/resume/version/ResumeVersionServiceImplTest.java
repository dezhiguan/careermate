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
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
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
                agentArtifactService,
                new com.careermate.resume.version.verify.ResumeFactVerifier()
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
                "腾讯",
                "算法工程师",
                "# 简历\n内容",
                "对比原简历，我强化了技能表达。",
                List.of(Map.of("field", "技能", "change", "对齐 JD"))
        );

        ArgumentCaptor<ResumeVersionEntity> captor = ArgumentCaptor.forClass(ResumeVersionEntity.class);
        verify(resumeVersionMapper).insert(captor.capture());
        ResumeVersionEntity saved = captor.getValue();
        assertEquals(1L, saved.getUserId());
        assertEquals("WS-abc", saved.getSessionId());
        assertEquals(99L, saved.getSourceResumeId());
        assertEquals(1L, saved.getTargetJdId());
        assertEquals("腾讯", saved.getTargetCompany());
        assertEquals("算法工程师", saved.getTargetJdTitle());
        assertEquals(1, saved.getVersionSeq());
        assertEquals("针对【腾讯】算法工程师 · v1", saved.getVersionName());
        assertEquals("对比原简历，我强化了技能表达。", saved.getChangeSummary());
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
    void updateVersionForksWhenEditingGeneratedVersionContent() {
        ResumeVersionEntity entity = entity("v-1", LocalDateTime.now());
        entity.setUserId(1L);
        entity.setOrigin("GENERATED");
        entity.setContentMarkdown("# 原内容");
        when(resumeVersionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        var updated = service.updateVersion(1L, "v-1", "手改版", "# 新内容");

        // P4②=B：首次手改 AI 版内容 → fork 出 MANUAL_EDIT 新版本，指向原版
        ArgumentCaptor<ResumeVersionEntity> captor = ArgumentCaptor.forClass(ResumeVersionEntity.class);
        verify(resumeVersionMapper).insert(captor.capture());
        ResumeVersionEntity fork = captor.getValue();
        assertEquals("MANUAL_EDIT", fork.getOrigin());
        assertEquals("v-1", fork.getParentVersionId());
        assertEquals("# 新内容", fork.getContentMarkdown());
        assertEquals("# 原内容", entity.getContentMarkdown());
        assertEquals("MANUAL_EDIT", updated.origin());
    }

    @Test
    void updateVersionInPlaceWhenAlreadyManualEdit() {
        ResumeVersionEntity entity = entity("v-2", LocalDateTime.now());
        entity.setId(20L);
        entity.setUserId(1L);
        entity.setOrigin("MANUAL_EDIT");
        when(resumeVersionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        var updated = service.updateVersion(1L, "v-2", "新标题", "# 新内容");

        verify(resumeVersionMapper).updateById(entity);
        verify(resumeVersionMapper, never()).insert(any(ResumeVersionEntity.class));
        assertEquals("新标题", entity.getVersionName());
        assertEquals("# 新内容", entity.getContentMarkdown());
        assertEquals("MANUAL_EDIT", updated.origin());
    }

    @Test
    void updateVersionNameOnlyDoesNotForkGeneratedVersion() {
        ResumeVersionEntity entity = entity("v-4", LocalDateTime.now());
        entity.setUserId(1L);
        entity.setOrigin("GENERATED");
        when(resumeVersionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        service.updateVersion(1L, "v-4", "只改名", null);

        // 仅改名不 fork（仅内容编辑才 fork）
        verify(resumeVersionMapper).updateById(entity);
        verify(resumeVersionMapper, never()).insert(any(ResumeVersionEntity.class));
        assertEquals("只改名", entity.getVersionName());
    }

    @Test
    void updateVersionSaveVerifyFlagsNewUnsourcedFact() {
        ResumeVersionEntity entity = entity("v-3", LocalDateTime.now());
        entity.setUserId(1L);
        entity.setOrigin("MANUAL_EDIT");
        entity.setContentMarkdown("# 我的简历\n后端开发经验");
        when(resumeVersionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        var updated = service.updateVersion(1L, "v-3", null, "# 我的简历\n阿里巴巴集团 高级工程师");

        // P4①=A：编辑新增了编辑前没有的「阿里巴巴集团」→ factCheck 标记（仅提示不拦截）
        assertNotNull(entity.getFactCheck());
        assertTrue(entity.getFactCheck().contains("阿里巴巴集团"));
        assertNotNull(updated.factCheck());
    }

    @Test
    void createVersionRegistersResumeArtifact() {
        service.createVersion(
                1L,
                "WS-session",
                99L,
                "doc-1",
                "腾讯 算法工程师",
                "腾讯",
                "算法工程师",
                "# 简历\n内容",
                "对比原简历，我强化了技能表达。",
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
        assertEquals("针对【腾讯】算法工程师 · v1", artifact.title());
    }

    @Test
    void createVersionIncrementsSeqWithinSameUserAndTargetJd() {
        ResumeVersionEntity existing = new ResumeVersionEntity();
        existing.setVersionSeq(1);
        when(resumeVersionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(existing))
                .thenReturn(List.of());

        var second = service.createVersion(
                1L,
                "WS-a",
                99L,
                "doc-1",
                "腾讯 Java",
                "腾讯",
                "Java",
                "# 简历 A2",
                "对比原简历，我强化了 Java 项目。",
                List.of()
        );
        var firstForOtherJd = service.createVersion(
                1L,
                "WS-b",
                99L,
                "doc-2",
                "阿里 Java",
                "阿里",
                "Java",
                "# 简历 B1",
                "对比原简历，我强化了 Java 项目。",
                List.of()
        );

        assertEquals("针对【腾讯】Java · v2", second.versionName());
        assertEquals(2, second.versionSeq());
        assertEquals("针对【阿里】Java · v1", firstForOtherJd.versionName());
        assertEquals(1, firstForOtherJd.versionSeq());
    }

    @Test
    void createVersionRetriesWhenSeqCollides() {
        ResumeVersionEntity seqOne = new ResumeVersionEntity();
        seqOne.setVersionSeq(1);
        ResumeVersionEntity seqTwo = new ResumeVersionEntity();
        seqTwo.setVersionSeq(2);
        when(resumeVersionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(seqOne))
                .thenReturn(List.of(seqOne, seqTwo));
        List<Integer> attemptedSeqs = new ArrayList<>();
        when(resumeVersionMapper.insert(any(ResumeVersionEntity.class)))
                .thenAnswer(invocation -> {
                    ResumeVersionEntity entity = invocation.getArgument(0);
                    attemptedSeqs.add(entity.getVersionSeq());
                    throw new DuplicateKeyException("seq collision");
                })
                .thenAnswer(invocation -> {
                    ResumeVersionEntity entity = invocation.getArgument(0);
                    attemptedSeqs.add(entity.getVersionSeq());
                    return 1;
                });

        var saved = service.createVersion(
                1L,
                "WS-retry",
                99L,
                "doc-1",
                "腾讯 Java",
                "腾讯",
                "Java",
                "# 简历 A3",
                "对比原简历，我强化了 Java 项目。",
                List.of()
        );

        verify(resumeVersionMapper, times(2)).insert(any(ResumeVersionEntity.class));
        assertEquals(List.of(2, 3), attemptedSeqs);
        assertEquals(3, saved.versionSeq());
        assertEquals("针对【腾讯】Java · v3", saved.versionName());
    }

    @Test
    void createVersionKeepsLegacyTargetJdIdWhenRawCannotBeParsed() {
        service.createVersion(
                1L,
                "WS-legacy",
                99L,
                "weird-string-not-bigint",
                "未知 JD",
                "未知公司",
                "后端开发",
                "# 简历",
                "对比原简历，我强化了岗位关键词。",
                List.of()
        );

        ArgumentCaptor<ResumeVersionEntity> captor = ArgumentCaptor.forClass(ResumeVersionEntity.class);
        verify(resumeVersionMapper).insert(captor.capture());
        ResumeVersionEntity saved = captor.getValue();
        assertEquals(null, saved.getTargetJdId());
        assertEquals("weird-string-not-bigint", saved.getLegacyTargetJdIdRaw());
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
        entity.setTargetCompany("腾讯");
        entity.setTargetJdTitle("算法工程师");
        entity.setVersionSeq(1);
        entity.setContentMarkdown("md");
        entity.setCreatedAt(createdAt);
        return entity;
    }
}
