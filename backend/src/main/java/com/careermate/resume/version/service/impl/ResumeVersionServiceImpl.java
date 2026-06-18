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
            String targetCompany,
            String targetJdTitle,
            String versionName,
            String contentMarkdown,
            List<Map<String, Object>> optimizationNotes
    ) {
        LocalDateTime now = LocalDateTime.now();
        Long targetJdDocId = parseTargetJdId(targetJdId);
        int versionSeq = nextVersionSeq(userId, targetJdDocId);
        String safeCompany = fallbackText(targetCompany, "未知公司");
        String safeTitle = fallbackText(targetJdTitle, fallbackText(targetJdLabel, "历史定制简历"));
        ResumeVersionEntity entity = new ResumeVersionEntity();
        entity.setVersionId(UUID.randomUUID().toString());
        entity.setUserId(userId);
        entity.setTenantId(DEFAULT_TENANT_ID);
        entity.setSessionId(sessionId);
        entity.setSourceResumeId(sourceResumeId);
        entity.setTargetJdId(targetJdDocId);
        entity.setTargetJdLabel(targetJdLabel);
        entity.setTargetCompany(safeCompany);
        entity.setTargetJdTitle(safeTitle);
        entity.setVersionSeq(versionSeq);
        entity.setVersionName(versionName);
        entity.setContentMarkdown(contentMarkdown);
        entity.setOptimizationNotes(writeNotesJson(optimizationNotes));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        resumeVersionMapper.insert(entity);
        registerResumeVersionArtifact(
                userId,
                sessionId,
                sourceResumeId,
                targetJdDocId,
                targetJdLabel,
                safeCompany,
                safeTitle,
                versionSeq,
                displayName(entity),
                entity.getVersionId()
        );
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
            Long targetJdId,
            String targetJdLabel,
            String targetCompany,
            String targetJdTitle,
            Integer versionSeq,
            String versionName,
            String versionId
    ) {
        String displayName = versionName != null && !versionName.isBlank()
                ? versionName.trim()
                : buildDisplayName(targetCompany, targetJdTitle, versionSeq);
        String summary = targetJdTitle != null && !targetJdTitle.isBlank()
                ? "针对 " + fallbackText(targetCompany, "未知公司") + " " + targetJdTitle.trim() + " 生成的简历版本"
                : "AI 生成的简历版本";
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        if (sourceResumeId != null) {
            metadata.put("sourceResumeId", sourceResumeId);
        }
        if (targetJdId != null) {
            metadata.put("targetJdId", targetJdId);
        }
        if (targetJdLabel != null && !targetJdLabel.isBlank()) {
            metadata.put("targetJdLabel", targetJdLabel);
        }
        if (targetCompany != null && !targetCompany.isBlank()) {
            metadata.put("targetCompany", targetCompany);
        }
        if (targetJdTitle != null && !targetJdTitle.isBlank()) {
            metadata.put("targetJdTitle", targetJdTitle);
        }
        if (versionSeq != null) {
            metadata.put("versionSeq", versionSeq);
        }
        if (!displayName.isBlank()) {
            metadata.put("versionName", displayName);
        }
        agentArtifactService.create(new CreateAgentArtifactCommand(
                userId,
                sessionId,
                ArtifactConstants.TYPE_RESUME_VERSION,
                displayName,
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
                displayName(entity),
                entity.getTargetJdLabel(),
                entity.getTargetCompany(),
                entity.getTargetJdId(),
                entity.getTargetJdTitle(),
                entity.getVersionSeq(),
                toOffsetDateTime(entity.getCreatedAt())
        );
    }

    private ResumeVersionVO toDetailVO(ResumeVersionEntity entity) {
        return new ResumeVersionVO(
                entity.getVersionId(),
                displayName(entity),
                entity.getSessionId(),
                entity.getTargetJdId(),
                entity.getTargetJdLabel(),
                entity.getTargetCompany(),
                entity.getTargetJdTitle(),
                entity.getVersionSeq(),
                entity.getContentMarkdown(),
                parseNotes(entity.getOptimizationNotes()),
                entity.getAiScore(),
                toOffsetDateTime(entity.getCreatedAt())
        );
    }

    private int nextVersionSeq(Long userId, Long targetJdId) {
        LambdaQueryWrapper<ResumeVersionEntity> wrapper = new LambdaQueryWrapper<ResumeVersionEntity>()
                .eq(ResumeVersionEntity::getUserId, userId);
        if (targetJdId == null) {
            wrapper.isNull(ResumeVersionEntity::getTargetJdId);
        } else {
            wrapper.eq(ResumeVersionEntity::getTargetJdId, targetJdId);
        }
        List<ResumeVersionEntity> existing = resumeVersionMapper.selectList(wrapper);
        if (existing == null || existing.isEmpty()) {
            return 1;
        }
        return existing.stream()
                .map(ResumeVersionEntity::getVersionSeq)
                .filter(seq -> seq != null)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private static Long parseTargetJdId(String targetJdId) {
        if (targetJdId == null || targetJdId.isBlank()) {
            return null;
        }
        String raw = targetJdId.trim();
        if (raw.startsWith("doc-")) {
            raw = raw.substring(4).trim();
        }
        if (raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String displayName(ResumeVersionEntity entity) {
        if (entity == null) {
            return "针对【未知公司】历史定制简历 · v1";
        }
        return buildDisplayName(entity.getTargetCompany(), entity.getTargetJdTitle(), entity.getVersionSeq());
    }

    private static String buildDisplayName(String company, String title, Integer versionSeq) {
        return "针对【" + fallbackText(company, "未知公司") + "】"
                + fallbackText(title, "历史定制简历")
                + " · v" + (versionSeq == null || versionSeq < 1 ? 1 : versionSeq);
    }

    private static String fallbackText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
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
