package com.careermate.resume.version.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.artifact.ArtifactConstants;
import com.careermate.artifact.dto.CreateAgentArtifactCommand;
import com.careermate.artifact.service.AgentArtifactService;
import com.careermate.common.exception.BizException;
import com.careermate.mapper.ResumeVersionMapper;
import com.careermate.model.entity.ResumeVersionEntity;
import com.careermate.resume.version.dto.ResumeVersionListItemVO;
import com.careermate.resume.version.dto.ResumeVersionVO;
import com.careermate.resume.version.export.ResumeExportResponseHeaders;
import com.careermate.resume.version.export.ResumeVersionDocxRenderer;
import com.careermate.resume.version.export.ResumeVersionPdfRenderer;
import com.careermate.resume.version.service.ResumeVersionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class ResumeVersionServiceImpl implements ResumeVersionService {

    private static final long DEFAULT_TENANT_ID = 1L;

    private final ResumeVersionMapper resumeVersionMapper;
    private final ObjectMapper objectMapper;
    private final ResumeVersionPdfRenderer pdfRenderer;
    private final ResumeVersionDocxRenderer docxRenderer;
    private final AgentArtifactService agentArtifactService;

    public ResumeVersionServiceImpl(
            ResumeVersionMapper resumeVersionMapper,
            ObjectMapper objectMapper,
            ResumeVersionPdfRenderer pdfRenderer,
            ResumeVersionDocxRenderer docxRenderer,
            AgentArtifactService agentArtifactService
    ) {
        this.resumeVersionMapper = resumeVersionMapper;
        this.objectMapper = objectMapper;
        this.pdfRenderer = pdfRenderer;
        this.docxRenderer = docxRenderer;
        this.agentArtifactService = agentArtifactService;
    }

    @Override
    @Transactional(readOnly = true)
    public ResumeVersionVO getVersion(Long userId, String versionId) {
        ResumeVersionEntity entity = requireOwnedVersion(userId, versionId);
        return toDetailVO(entity);
    }

    @Override
    @Transactional
    public ResumeVersionVO updateVersion(Long userId, String versionId, String versionName, String contentMarkdown) {
        ResumeVersionEntity entity = requireOwnedVersion(userId, versionId);
        entity.setVersionName(versionName);
        entity.setContentMarkdown(contentMarkdown);
        entity.setUpdatedAt(LocalDateTime.now());
        resumeVersionMapper.updateById(entity);
        return toDetailVO(entity);
    }

    @Override
    @Transactional
    public void deleteVersion(Long userId, String versionId) {
        ResumeVersionEntity entity = requireOwnedVersion(userId, versionId);
        resumeVersionMapper.deleteById(entity.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResumeVersionListItemVO> listBySession(Long userId, String sessionId) {
        LambdaQueryWrapper<ResumeVersionEntity> wrapper = new LambdaQueryWrapper<ResumeVersionEntity>()
                .eq(ResumeVersionEntity::getUserId, userId)
                .orderByDesc(ResumeVersionEntity::getCreatedAt);
        if (sessionId != null && !sessionId.isBlank()) {
            wrapper.eq(ResumeVersionEntity::getSessionId, sessionId);
        }
        return resumeVersionMapper.selectList(wrapper).stream()
                .map(this::toListItemVO)
                .toList();
    }

    @Override
    @Transactional
    public ResumeVersionVO createVersion(
            Long userId,
            String sessionId,
            Long sourceResumeId,
            String targetJdId,
            String targetJdLabel,
            String versionName,
            String contentMarkdown,
            List<Map<String, Object>> optimizationNotes
    ) {
        LocalDateTime now = LocalDateTime.now();
        ResumeVersionEntity entity = new ResumeVersionEntity();
        entity.setVersionId(UUID.randomUUID().toString());
        entity.setUserId(userId);
        entity.setTenantId(DEFAULT_TENANT_ID);
        entity.setSessionId(sessionId);
        entity.setSourceResumeId(sourceResumeId);
        entity.setTargetJdId(targetJdId);
        entity.setTargetJdLabel(targetJdLabel);
        entity.setVersionName(versionName);
        entity.setContentMarkdown(contentMarkdown);
        entity.setOptimizationNotes(writeNotesJson(optimizationNotes));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        resumeVersionMapper.insert(entity);
        registerResumeVersionArtifact(userId, sessionId, sourceResumeId, targetJdId, targetJdLabel, versionName, entity.getVersionId());
        return toDetailVO(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public void exportPdf(Long userId, String versionId, HttpServletResponse response) {
        ResumeVersionEntity entity = requireOwnedVersion(userId, versionId);
        try {
            ResumeExportResponseHeaders.pdf(response, entity.getVersionName());
            pdfRenderer.render(entity.getContentMarkdown(), response.getOutputStream());
            response.flushBuffer();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("PDF 导出失败, versionId={}", versionId, e);
            throw new BizException(500, "PDF 导出失败");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void exportDocx(Long userId, String versionId, HttpServletResponse response) {
        ResumeVersionEntity entity = requireOwnedVersion(userId, versionId);
        try {
            ResumeExportResponseHeaders.docx(response, entity.getVersionName());
            docxRenderer.render(entity.getContentMarkdown(), response.getOutputStream());
            response.flushBuffer();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Word 导出失败, versionId={}", versionId, e);
            throw new BizException(500, "Word 导出失败");
        }
    }

    private void registerResumeVersionArtifact(
            Long userId,
            String sessionId,
            Long sourceResumeId,
            String targetJdId,
            String targetJdLabel,
            String versionName,
            String versionId
    ) {
        String summary = targetJdLabel != null && !targetJdLabel.isBlank()
                ? "针对 " + targetJdLabel.trim() + " 生成的简历版本"
                : "AI 生成的简历版本";
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        if (sourceResumeId != null) {
            metadata.put("sourceResumeId", sourceResumeId);
        }
        if (targetJdId != null && !targetJdId.isBlank()) {
            metadata.put("targetJdId", targetJdId);
        }
        if (targetJdLabel != null && !targetJdLabel.isBlank()) {
            metadata.put("targetJdLabel", targetJdLabel);
        }
        if (versionName != null && !versionName.isBlank()) {
            metadata.put("versionName", versionName);
        }
        agentArtifactService.create(new CreateAgentArtifactCommand(
                userId,
                sessionId,
                ArtifactConstants.TYPE_RESUME_VERSION,
                versionName,
                summary,
                ArtifactConstants.REF_RESUME_VERSION,
                versionId,
                metadata
        ));
    }

    private ResumeVersionEntity requireOwnedVersion(Long userId, String versionId) {
        if (userId == null) {
            throw new BizException(401, "未认证");
        }
        ResumeVersionEntity entity = resumeVersionMapper.selectOne(
                new LambdaQueryWrapper<ResumeVersionEntity>()
                        .eq(ResumeVersionEntity::getVersionId, versionId)
                        .last("LIMIT 1")
        );
        if (entity == null) {
            throw new BizException(404, "简历版本不存在");
        }
        if (!userId.equals(entity.getUserId())) {
            throw new BizException(403, "无权访问该简历版本");
        }
        return entity;
    }

    private ResumeVersionListItemVO toListItemVO(ResumeVersionEntity entity) {
        return new ResumeVersionListItemVO(
                entity.getVersionId(),
                entity.getVersionName(),
                entity.getTargetJdLabel(),
                toOffsetDateTime(entity.getCreatedAt())
        );
    }

    private ResumeVersionVO toDetailVO(ResumeVersionEntity entity) {
        return new ResumeVersionVO(
                entity.getVersionId(),
                entity.getVersionName(),
                entity.getSessionId(),
                entity.getTargetJdId(),
                entity.getTargetJdLabel(),
                entity.getContentMarkdown(),
                parseNotes(entity.getOptimizationNotes()),
                entity.getAiScore(),
                toOffsetDateTime(entity.getCreatedAt())
        );
    }

    private List<Map<String, Object>> parseNotes(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("parse optimization_notes failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String writeNotesJson(List<Map<String, Object>> notes) {
        List<Map<String, Object>> safe = notes == null ? List.of() : notes;
        try {
            return objectMapper.writeValueAsString(safe);
        } catch (Exception e) {
            log.warn("write optimization_notes failed: {}", e.getMessage());
            return "[]";
        }
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atOffset(ZoneOffset.UTC);
    }
}
