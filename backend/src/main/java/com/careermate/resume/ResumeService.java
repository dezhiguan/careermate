package com.careermate.resume;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.careermate.common.exception.BizException;
import com.careermate.mapper.ResumeMapper;
import com.careermate.model.entity.ResumeEntity;
import com.careermate.ragforge.RagForgeClient;
import com.careermate.resume.dto.ResumeCreateRequest;
import com.careermate.resume.dto.ResumeDetailResponse;
import com.careermate.resume.dto.ResumeListItemResponse;
import com.careermate.resume.dto.ResumeUpdateRequest;
import com.careermate.resume.version.export.ResumeExportResponseHeaders;
import com.careermate.resume.version.export.ResumeVersionDocxRenderer;
import com.careermate.resume.version.export.ResumeVersionPdfRenderer;
import com.careermate.security.CurrentUserContext;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ResumeService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DELETED = "DELETED";
    public static final String SOURCE_TYPE_TEXT = "TEXT";
    public static final String SOURCE_TYPE_FILE = "FILE";
    private static final int PREVIEW_MAX_LEN = 120;

    private final ResumeMapper resumeMapper;
    private final RagForgeClient ragForgeClient;
    private final ResumeFileParserService fileParserService;
    private final ResumeVersionPdfRenderer pdfRenderer;
    private final ResumeVersionDocxRenderer docxRenderer;

    public ResumeService(ResumeMapper resumeMapper,
                         RagForgeClient ragForgeClient,
                         ResumeFileParserService fileParserService,
                         ResumeVersionPdfRenderer pdfRenderer,
                         ResumeVersionDocxRenderer docxRenderer) {
        this.resumeMapper = resumeMapper;
        this.ragForgeClient = ragForgeClient;
        this.fileParserService = fileParserService;
        this.pdfRenderer = pdfRenderer;
        this.docxRenderer = docxRenderer;
    }

    public Optional<ResumeEntity> getDefaultActiveResume(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        ResumeEntity entity = resumeMapper.selectOne(
                new LambdaQueryWrapper<ResumeEntity>()
                        .eq(ResumeEntity::getUserId, userId)
                        .eq(ResumeEntity::getStatus, STATUS_ACTIVE)
                        .eq(ResumeEntity::getIsDefault, true)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(entity);
    }

    public List<ResumeListItemResponse> listActiveResumes() {
        Long userId = requireUserId();
        List<ResumeEntity> rows = resumeMapper.selectList(
                new LambdaQueryWrapper<ResumeEntity>()
                        .eq(ResumeEntity::getUserId, userId)
                        .eq(ResumeEntity::getStatus, STATUS_ACTIVE)
                        .orderByDesc(ResumeEntity::getCreatedAt)
        );
        return rows.stream().map(this::toListItem).toList();
    }

    @Transactional
    public ResumeDetailResponse createResume(ResumeCreateRequest request) {
        Long userId = requireUserId();
        OffsetDateTime now = OffsetDateTime.now();

        boolean hasDefault = resumeMapper.selectCount(
                new LambdaQueryWrapper<ResumeEntity>()
                        .eq(ResumeEntity::getUserId, userId)
                        .eq(ResumeEntity::getStatus, STATUS_ACTIVE)
                        .eq(ResumeEntity::getIsDefault, true)
        ) > 0;

        ResumeEntity entity = new ResumeEntity();
        entity.setUserId(userId);
        entity.setTitle(request.getTitle().trim());
        entity.setContent(request.getContent().trim());
        entity.setSourceType(SOURCE_TYPE_TEXT);
        entity.setIsDefault(!hasDefault);
        entity.setStatus(STATUS_ACTIVE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        insertResumeEntity(entity);

        // 异步同步到 RAGForge Personal KB，不阻塞主流程
        asyncSyncToRag(entity.getId(), entity.getTitle(), entity.getContent(), null);

        return toDetail(entity);
    }

    public ResumeDetailResponse getResume(Long id) {
        Long userId = requireUserId();
        ResumeEntity entity = getActiveResumeOrThrow(userId, id);
        return toDetail(entity);
    }

    @Transactional(readOnly = true)
    public void exportPdf(Long id, HttpServletResponse response) {
        Long userId = requireUserId();
        ResumeEntity entity = getActiveResumeOrThrow(userId, id);
        try {
            ResumeExportResponseHeaders.pdf(response, entity.getTitle());
            pdfRenderer.render(entity.getContent(), response.getOutputStream());
            response.flushBuffer();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("默认简历 PDF 导出失败, resumeId={}", id, e);
            throw new BizException(500, "PDF 导出失败");
        }
    }

    @Transactional(readOnly = true)
    public void exportDocx(Long id, HttpServletResponse response) {
        Long userId = requireUserId();
        ResumeEntity entity = getActiveResumeOrThrow(userId, id);
        try {
            ResumeExportResponseHeaders.docx(response, entity.getTitle());
            docxRenderer.render(entity.getContent(), response.getOutputStream());
            response.flushBuffer();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("默认简历 Word 导出失败, resumeId={}", id, e);
            throw new BizException(500, "Word 导出失败");
        }
    }

    @Transactional
    public ResumeDetailResponse updateResume(Long id, ResumeUpdateRequest request) {
        Long userId = requireUserId();
        ResumeEntity entity = getActiveResumeOrThrow(userId, id);
        entity.setTitle(request.getTitle().trim());
        entity.setContent(request.getContent().trim());
        entity.setUpdatedAt(OffsetDateTime.now());
        resumeMapper.updateById(entity);

        // 异步替换 RAGForge 里的旧文档
        Long oldRagDocId = entity.getRagDocId();
        asyncReplaceRagDoc(entity.getId(), entity.getTitle(), entity.getContent(), oldRagDocId);

        return toDetail(entity);
    }

    @Transactional
    public void deleteResume(Long id) {
        Long userId = requireUserId();
        ResumeEntity entity = getActiveResumeOrThrow(userId, id);
        boolean wasDefault = Boolean.TRUE.equals(entity.getIsDefault());
        OffsetDateTime now = OffsetDateTime.now();

        resumeMapper.update(
                null,
                new LambdaUpdateWrapper<ResumeEntity>()
                        .eq(ResumeEntity::getId, id)
                        .eq(ResumeEntity::getUserId, userId)
                        .set(ResumeEntity::getStatus, STATUS_DELETED)
                        .set(ResumeEntity::getIsDefault, false)
                        .set(ResumeEntity::getUpdatedAt, now)
        );

        if (wasDefault) {
            promoteLatestActiveAsDefault(userId);
        }

        // 异步删除 RAGForge 里对应的文档
        Long ragDocId = entity.getRagDocId();
        if (ragDocId != null) {
            java.util.concurrent.CompletableFuture.runAsync(() ->
                ragForgeClient.deleteDocument(ragDocId));
        }
    }

    @Transactional
    public ResumeDetailResponse setDefaultResume(Long id) {
        Long userId = requireUserId();
        getActiveResumeOrThrow(userId, id);
        OffsetDateTime now = OffsetDateTime.now();

        resumeMapper.update(
                null,
                new LambdaUpdateWrapper<ResumeEntity>()
                        .eq(ResumeEntity::getUserId, userId)
                        .eq(ResumeEntity::getStatus, STATUS_ACTIVE)
                        .set(ResumeEntity::getIsDefault, false)
                        .set(ResumeEntity::getUpdatedAt, now)
        );

        try {
            resumeMapper.update(
                    null,
                    new LambdaUpdateWrapper<ResumeEntity>()
                            .eq(ResumeEntity::getId, id)
                            .eq(ResumeEntity::getUserId, userId)
                            .eq(ResumeEntity::getStatus, STATUS_ACTIVE)
                            .set(ResumeEntity::getIsDefault, true)
                            .set(ResumeEntity::getUpdatedAt, now)
            );
        } catch (DuplicateKeyException ex) {
            throw new BizException(409, "设置默认简历冲突，请稍后重试");
        }

        return getResume(id);
    }

    @Transactional
    public ResumeDetailResponse uploadResumeFile(String title, MultipartFile file) {
        Long userId = requireUserId();

        // 解析文件内容
        String content = fileParserService.parse(file);

        // 标题：优先用传入的，否则用文件名（去掉扩展名）
        String resolvedTitle = (title != null && !title.isBlank())
                ? title.trim()
                : stripExtension(file.getOriginalFilename());

        OffsetDateTime now = OffsetDateTime.now();

        boolean hasDefault = resumeMapper.selectCount(
                new LambdaQueryWrapper<ResumeEntity>()
                        .eq(ResumeEntity::getUserId, userId)
                        .eq(ResumeEntity::getStatus, STATUS_ACTIVE)
                        .eq(ResumeEntity::getIsDefault, true)
        ) > 0;

        ResumeEntity entity = new ResumeEntity();
        entity.setUserId(userId);
        entity.setTitle(resolvedTitle);
        entity.setContent(content);
        entity.setSourceType(SOURCE_TYPE_FILE);
        entity.setIsDefault(!hasDefault);
        entity.setStatus(STATUS_ACTIVE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        insertResumeEntity(entity);

        // 同步到 RAGForge（复用已有异步逻辑）
        asyncSyncToRag(entity.getId(), entity.getTitle(), entity.getContent(), null);

        return toDetail(entity);
    }

    private String stripExtension(String filename) {
        if (filename == null || filename.isBlank()) return "我的简历";
        int idx = filename.lastIndexOf('.');
        return idx > 0 ? filename.substring(0, idx) : filename;
    }

    private void insertResumeEntity(ResumeEntity entity) {
        try {
            resumeMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            if (!Boolean.TRUE.equals(entity.getIsDefault())) {
                throw ex;
            }
            entity.setIsDefault(false);
            resumeMapper.insert(entity);
        }
    }

    private void promoteLatestActiveAsDefault(Long userId) {
        ResumeEntity latest = resumeMapper.selectOne(
                new LambdaQueryWrapper<ResumeEntity>()
                        .eq(ResumeEntity::getUserId, userId)
                        .eq(ResumeEntity::getStatus, STATUS_ACTIVE)
                        .orderByDesc(ResumeEntity::getCreatedAt)
                        .last("LIMIT 1")
        );
        if (latest == null) {
            return;
        }
        latest.setIsDefault(true);
        latest.setUpdatedAt(OffsetDateTime.now());
        resumeMapper.updateById(latest);
    }

    private ResumeEntity getActiveResumeOrThrow(Long userId, Long id) {
        ResumeEntity entity = resumeMapper.selectOne(
                new LambdaQueryWrapper<ResumeEntity>()
                        .eq(ResumeEntity::getId, id)
                        .eq(ResumeEntity::getUserId, userId)
                        .eq(ResumeEntity::getStatus, STATUS_ACTIVE)
        );
        if (entity == null) {
            throw new BizException(404, "简历不存在");
        }
        return entity;
    }

    private Long requireUserId() {
        Long userId = CurrentUserContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未认证");
        }
        return userId;
    }

    private ResumeListItemResponse toListItem(ResumeEntity entity) {
        return ResumeListItemResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .sourceType(entity.getSourceType())
                .isDefault(entity.getIsDefault())
                .status(entity.getStatus())
                .contentPreview(buildPreview(entity.getContent()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private ResumeDetailResponse toDetail(ResumeEntity entity) {
        return ResumeDetailResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .sourceType(entity.getSourceType())
                .isDefault(entity.getIsDefault())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String buildPreview(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= PREVIEW_MAX_LEN) {
            return normalized;
        }
        return normalized.substring(0, PREVIEW_MAX_LEN) + "...";
    }

    private void asyncSyncToRag(Long resumeId, String title, String content, Long ignoredOldDocId) {
        Long kbId = parsePersonalKbId();
        if (kbId == null) return;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            Optional<Long> result = ragForgeClient.syncText(kbId, title, content, "RESUME");
            result.ifPresent(docId ->
                resumeMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ResumeEntity>()
                        .eq(ResumeEntity::getId, resumeId)
                        .set(ResumeEntity::getRagDocId, docId))
            );
        });
    }

    private void asyncReplaceRagDoc(Long resumeId, String title, String content, Long oldRagDocId) {
        Long kbId = parsePersonalKbId();
        if (kbId == null) return;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            if (oldRagDocId != null) {
                ragForgeClient.deleteDocument(oldRagDocId);
            }
            Optional<Long> result = ragForgeClient.syncText(kbId, title, content, "RESUME");
            result.ifPresent(docId ->
                resumeMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ResumeEntity>()
                        .eq(ResumeEntity::getId, resumeId)
                        .set(ResumeEntity::getRagDocId, docId))
            );
        });
    }

    private Long parsePersonalKbId() {
        String raw = ragForgeClient.getProperties().getPersonalKbId();
        if (raw == null || raw.isBlank()) return null;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
