package com.careermate.agent.memory.ltm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.mapper.UserLongTermMemoryMapper;
import com.careermate.model.entity.UserLongTermMemoryEntity;
import com.careermate.ragforge.RagForgeChunk;
import com.careermate.ragforge.RagForgeClient;
import com.careermate.ragforge.RagForgeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A4：长期记忆存取。默认走知识库（RAGForge KB）后端——存储=同步文本到记忆库拿 docId；
 * 召回=按该用户的 docId 集合收窄 KB 检索（用户级隔离）；不依赖 pgvector。
 * 保留 PGVECTOR 模式（embedding→cosine）可回退。禁用/未配库/降级时全部安全 no-op。
 */
@Slf4j
@Service
public class LongTermMemoryService {

    private final EmbeddingClient embeddingClient;
    private final UserLongTermMemoryMapper mapper;
    private final LtmProperties properties;
    private final RagForgeClient ragForgeClient;
    private final RagForgeProperties ragForgeProperties;

    public LongTermMemoryService(EmbeddingClient embeddingClient, UserLongTermMemoryMapper mapper,
                                 LtmProperties properties, RagForgeClient ragForgeClient,
                                 RagForgeProperties ragForgeProperties) {
        this.embeddingClient = embeddingClient;
        this.mapper = mapper;
        this.properties = properties;
        this.ragForgeClient = ragForgeClient;
        this.ragForgeProperties = ragForgeProperties;
    }

    /** 召回与 query 相关、超过阈值的 fact。 */
    public List<LtmMatch> recall(Long userId, String query) {
        if (!properties.isEnabled() || userId == null || !StringUtils.hasText(query)) {
            return List.of();
        }
        return properties.isKbMode() ? recallFromKb(userId, query) : recallFromPgVector(userId, query);
    }

    /** 存 fact：同 type 高相似则 supersede 旧的并提升置信，否则新增。返回是否落库。 */
    public boolean store(Long userId, String factType, String factText, double confidence) {
        if (!properties.isEnabled() || userId == null || !StringUtils.hasText(factText)) {
            return false;
        }
        return properties.isKbMode()
                ? storeToKb(userId, factType, factText, confidence)
                : storeToPgVector(userId, factType, factText, confidence);
    }

    /** 用户"忘掉这条"=软删；KB 模式下同时删除记忆库中的文档。 */
    public void forget(Long userId, Long id) {
        if (userId == null || id == null) {
            return;
        }
        if (properties.isKbMode()) {
            UserLongTermMemoryEntity e = mapper.selectById(id);
            if (e != null && userId.equals(e.getUserId()) && e.getRagDocId() != null) {
                ragForgeClient.deleteDocument(e.getRagDocId());
            }
        }
        mapper.softDelete(userId, id);
    }

    /** /mine 展示：活跃（未软删未被取代）且置信达标的 fact。 */
    public List<UserLongTermMemoryEntity> listActive(Long userId) {
        return mapper.selectList(new LambdaQueryWrapper<UserLongTermMemoryEntity>()
                .eq(UserLongTermMemoryEntity::getUserId, userId)
                .eq(UserLongTermMemoryEntity::getDeleted, false)
                .isNull(UserLongTermMemoryEntity::getSupersededBy)
                .ge(UserLongTermMemoryEntity::getConfidence, 0.4)
                .orderByDesc(UserLongTermMemoryEntity::getUpdatedAt));
    }

    // ----------------------------------------------------------------------------- KB backend

    private Long resolveLtmKbId() {
        String raw = ragForgeProperties == null ? null : ragForgeProperties.getLtmKbId();
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("careermate.ragforge.ltm-kb-id 配置非数字，跳过 KB 记忆链路: {}", raw);
            return null;
        }
    }

    private List<LtmMatch> recallFromKb(Long userId, String query) {
        Long kbId = resolveLtmKbId();
        if (kbId == null) {
            return List.of();
        }
        try {
            List<UserLongTermMemoryEntity> active = listActive(userId);
            Map<Long, UserLongTermMemoryEntity> byDocId = new java.util.HashMap<>();
            List<Long> docIds = new ArrayList<>();
            for (UserLongTermMemoryEntity e : active) {
                if (e.getRagDocId() != null) {
                    docIds.add(e.getRagDocId());
                    byDocId.put(e.getRagDocId(), e);
                }
            }
            if (docIds.isEmpty()) {
                return List.of();
            }
            List<RagForgeChunk> chunks = ragForgeClient.searchInKb(
                    kbId, query, properties.getRecallTopK(), docIds, null);
            List<LtmMatch> matches = new ArrayList<>();
            for (RagForgeChunk c : chunks) {
                if (c.finalScore() != null && c.finalScore() < properties.getRecallThreshold()) {
                    continue;
                }
                UserLongTermMemoryEntity e = c.docId() == null ? null : byDocId.get(c.docId());
                if (e == null) {
                    continue;
                }
                LtmMatch m = new LtmMatch();
                m.setId(e.getId());
                m.setFactType(e.getFactType());
                m.setFactText(e.getFactText());
                m.setConfidence(e.getConfidence());
                m.setScore(c.finalScore());
                matches.add(m);
            }
            return matches;
        } catch (Exception e) {
            log.warn("长期记忆召回失败（KB，静默降级）: {}", e.getMessage());
            return List.of();
        }
    }

    private boolean storeToKb(Long userId, String factType, String factText, double confidence) {
        Long kbId = resolveLtmKbId();
        if (kbId == null) {
            return false;
        }
        try {
            // 同 type 语义去重：在该用户同 type 的 docId 集合内检索新 fact，命中≥阈值视为更新/矛盾。
            UserLongTermMemoryEntity supersedeTarget = findSupersedeTargetInKb(userId, factType, factText, kbId);
            double boosted = confidence;
            if (supersedeTarget != null) {
                boosted = Math.min(1.0,
                        (supersedeTarget.getConfidence() == null ? confidence : supersedeTarget.getConfidence()) + 0.1);
            }

            String title = "[" + factType + "] u" + userId;
            Optional<Long> docId = ragForgeClient.syncText(kbId, title, factText, factType);
            if (docId.isEmpty()) {
                return false;
            }

            UserLongTermMemoryEntity e = new UserLongTermMemoryEntity();
            e.setUserId(userId);
            e.setFactType(factType);
            e.setFactText(factText);
            e.setConfidence(boosted);
            e.setRagDocId(docId.get());
            e.setDeleted(false);
            mapper.insert(e);

            if (supersedeTarget != null && e.getId() != null) {
                mapper.markSuperseded(supersedeTarget.getId(), e.getId());
                if (supersedeTarget.getRagDocId() != null) {
                    ragForgeClient.deleteDocument(supersedeTarget.getRagDocId());
                }
            }
            return true;
        } catch (Exception ex) {
            log.warn("长期记忆存储失败（KB，静默降级）: {}", ex.getMessage());
            return false;
        }
    }

    private UserLongTermMemoryEntity findSupersedeTargetInKb(Long userId, String factType, String factText, Long kbId) {
        List<UserLongTermMemoryEntity> active = listActive(userId);
        Map<Long, UserLongTermMemoryEntity> sameTypeByDocId = new java.util.HashMap<>();
        List<Long> docIds = new ArrayList<>();
        for (UserLongTermMemoryEntity e : active) {
            if (factType.equalsIgnoreCase(e.getFactType()) && e.getRagDocId() != null) {
                docIds.add(e.getRagDocId());
                sameTypeByDocId.put(e.getRagDocId(), e);
            }
        }
        if (docIds.isEmpty()) {
            return null;
        }
        List<RagForgeChunk> hits = ragForgeClient.searchInKb(kbId, factText, 1, docIds, List.of(factType));
        for (RagForgeChunk c : hits) {
            if (c.finalScore() != null && c.finalScore() >= properties.getDuplicateThreshold()) {
                UserLongTermMemoryEntity e = c.docId() == null ? null : sameTypeByDocId.get(c.docId());
                if (e != null) {
                    return e;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------- PGVECTOR backend（回退）

    private List<LtmMatch> recallFromPgVector(Long userId, String query) {
        Optional<float[]> vec = embeddingClient.embed(query);
        if (vec.isEmpty()) {
            return List.of();
        }
        try {
            String pg = VectorFormat.toPgVector(vec.get());
            return mapper.cosineTopK(userId, pg, properties.getRecallTopK()).stream()
                    .filter(m -> m.getScore() != null && m.getScore() >= properties.getRecallThreshold())
                    .toList();
        } catch (Exception e) {
            log.warn("长期记忆召回失败（pgvector，静默降级）: {}", e.getMessage());
            return List.of();
        }
    }

    private boolean storeToPgVector(Long userId, String factType, String factText, double confidence) {
        Optional<float[]> vec = embeddingClient.embed(factText);
        if (vec.isEmpty()) {
            return false;
        }
        try {
            String pg = VectorFormat.toPgVector(vec.get());
            LtmMatch near = mapper.nearestSameType(userId, factType, pg);
            double boosted = confidence;
            Long supersedeId = null;
            if (near != null && near.getScore() != null && near.getScore() >= properties.getDuplicateThreshold()) {
                supersedeId = near.getId();
                boosted = Math.min(1.0, (near.getConfidence() == null ? confidence : near.getConfidence()) + 0.1);
            }
            UserLongTermMemoryEntity e = new UserLongTermMemoryEntity();
            e.setUserId(userId);
            e.setFactType(factType);
            e.setFactText(factText);
            e.setConfidence(boosted);
            e.setEmbedding(pg);
            e.setDeleted(false);
            mapper.insertWithEmbedding(e);
            if (supersedeId != null && e.getId() != null) {
                mapper.markSuperseded(supersedeId, e.getId());
            }
            return true;
        } catch (Exception ex) {
            log.warn("长期记忆存储失败（pgvector，静默降级）: {}", ex.getMessage());
            return false;
        }
    }
}
