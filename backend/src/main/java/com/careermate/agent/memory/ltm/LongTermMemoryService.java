package com.careermate.agent.memory.ltm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.careermate.mapper.UserLongTermMemoryMapper;
import com.careermate.model.entity.UserLongTermMemoryEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * A4：长期记忆存取。召回=embed→cosine top-k→阈值过滤；存储=embed→同 type 高相似则 supersede 旧 fact
 * （保留链不删旧）+ 提升 confidence，否则新增。全部依赖 embedding，禁用/无向量时静默降级为 no-op。
 */
@Slf4j
@Service
public class LongTermMemoryService {

    private final EmbeddingClient embeddingClient;
    private final UserLongTermMemoryMapper mapper;
    private final LtmProperties properties;

    public LongTermMemoryService(EmbeddingClient embeddingClient, UserLongTermMemoryMapper mapper,
                                 LtmProperties properties) {
        this.embeddingClient = embeddingClient;
        this.mapper = mapper;
        this.properties = properties;
    }

    /** 召回与 query 相关、超过阈值的 fact。 */
    public List<LtmMatch> recall(Long userId, String query) {
        if (!properties.isEnabled() || userId == null) {
            return List.of();
        }
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
            log.warn("长期记忆召回失败（静默降级）: {}", e.getMessage());
            return List.of();
        }
    }

    /** 存 fact：同 type 高相似则 supersede 旧的并提升置信，否则新增。返回是否落库。 */
    public boolean store(Long userId, String factType, String factText, double confidence) {
        if (!properties.isEnabled() || userId == null || !StringUtils.hasText(factText)) {
            return false;
        }
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
                // 高相似=更新/矛盾：supersede 旧 fact，新 fact 置信 +0.1（保留追溯链）
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
            log.warn("长期记忆存储失败（静默降级）: {}", ex.getMessage());
            return false;
        }
    }

    /** 用户"忘掉这条"=软删。 */
    public void forget(Long userId, Long id) {
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
}
