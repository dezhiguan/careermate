package com.careermate.artifact.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.artifact.ArtifactConstants;
import com.careermate.artifact.dto.AgentArtifactVO;
import com.careermate.artifact.dto.CreateAgentArtifactCommand;
import com.careermate.artifact.service.AgentArtifactService;
import com.careermate.common.exception.BizException;
import com.careermate.mapper.AgentArtifactMapper;
import com.careermate.model.entity.AgentArtifactEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class AgentArtifactServiceImpl implements AgentArtifactService {

    private final AgentArtifactMapper agentArtifactMapper;
    private final ObjectMapper objectMapper;

    public AgentArtifactServiceImpl(AgentArtifactMapper agentArtifactMapper, ObjectMapper objectMapper) {
        this.agentArtifactMapper = agentArtifactMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public AgentArtifactVO create(CreateAgentArtifactCommand command) {
        validateCreateCommand(command);
        rejectContentMarkdown(command.metadata());

        LocalDateTime now = LocalDateTime.now();
        AgentArtifactEntity entity = new AgentArtifactEntity();
        entity.setArtifactId(UUID.randomUUID().toString());
        entity.setUserId(command.userId());
        entity.setSessionId(command.sessionId());
        entity.setArtifactType(command.artifactType());
        entity.setTitle(command.title());
        entity.setSummary(command.summary());
        entity.setRefType(command.refType());
        entity.setRefId(command.refId());
        entity.setMetadata(writeMetadataJson(command.metadata()));
        entity.setStatus(ArtifactConstants.STATUS_ACTIVE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        agentArtifactMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentArtifactVO> listRecent(Long userId, int limit) {
        return list(userId, null, null, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentArtifactVO> list(Long userId, String artifactType, String sessionId, int limit) {
        if (userId == null) {
            throw new BizException(401, "未认证");
        }
        int safeLimit = normalizeLimit(limit);
        LambdaQueryWrapper<AgentArtifactEntity> wrapper = new LambdaQueryWrapper<AgentArtifactEntity>()
                .eq(AgentArtifactEntity::getUserId, userId)
                .eq(AgentArtifactEntity::getStatus, ArtifactConstants.STATUS_ACTIVE)
                .orderByDesc(AgentArtifactEntity::getCreatedAt)
                .last("LIMIT " + safeLimit);
        if (artifactType != null && !artifactType.isBlank()) {
            wrapper.eq(AgentArtifactEntity::getArtifactType, artifactType.trim());
        }
        if (sessionId != null && !sessionId.isBlank()) {
            wrapper.eq(AgentArtifactEntity::getSessionId, sessionId.trim());
        }
        return agentArtifactMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .toList();
    }

    static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 10;
        }
        return Math.min(limit, ArtifactConstants.MAX_LIST_LIMIT);
    }

    private void validateCreateCommand(CreateAgentArtifactCommand command) {
        if (command == null || command.userId() == null) {
            throw new BizException(400, "产物归属用户不能为空");
        }
        if (command.artifactType() == null || command.artifactType().isBlank()) {
            throw new BizException(400, "产物类型不能为空");
        }
        if (command.title() == null || command.title().isBlank()) {
            throw new BizException(400, "产物标题不能为空");
        }
        if (command.refType() == null || command.refType().isBlank()) {
            throw new BizException(400, "产物引用类型不能为空");
        }
        if (command.refId() == null || command.refId().isBlank()) {
            throw new BizException(400, "产物引用 ID 不能为空");
        }
    }

    private void rejectContentMarkdown(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        if (metadata.containsKey("contentMarkdown")) {
            throw new BizException(400, "产物索引不得保存正文 contentMarkdown");
        }
    }

    private AgentArtifactVO toVO(AgentArtifactEntity entity) {
        return new AgentArtifactVO(
                entity.getArtifactId(),
                entity.getArtifactType(),
                entity.getTitle(),
                entity.getSummary(),
                entity.getRefType(),
                entity.getRefId(),
                entity.getSessionId(),
                parseMetadata(entity.getMetadata()),
                toOffsetDateTime(entity.getCreatedAt()),
                resolveActionLabel(entity.getArtifactType()),
                resolveActionRoute(entity.getArtifactType(), entity.getSessionId())
        );
    }

    static String resolveActionLabel(String artifactType) {
        if (ArtifactConstants.TYPE_RESUME_VERSION.equals(artifactType)) {
            return "继续优化";
        }
        return null;
    }

    static String resolveActionRoute(String artifactType, String sessionId) {
        if (ArtifactConstants.TYPE_RESUME_VERSION.equals(artifactType)) {
            if (sessionId != null && !sessionId.isBlank()) {
                return "/chat/" + sessionId;
            }
            return "/chat";
        }
        return null;
    }

    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("parse artifact metadata failed: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String writeMetadataJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            throw new BizException(400, "产物 metadata 序列化失败");
        }
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atOffset(ZoneOffset.UTC);
    }
}
