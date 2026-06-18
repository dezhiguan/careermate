package com.careermate.workspace.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.careermate.common.exception.BizException;
import com.careermate.mapper.AgentMessageMapper;
import com.careermate.mapper.AgentSessionMapper;
import com.careermate.mapper.AgentTaskStateMapper;
import com.careermate.model.entity.AgentMessageEntity;
import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.model.entity.AgentTaskStateEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Repository
public class WorkspaceSessionRepository {

    public static final String WORKSPACE_JD_PREP = "JD_PREP";
    public static final String WORKSPACE_INTERVIEW = "INTERVIEW";
    public static final String WORKSPACE_MARKET = "MARKET";
    public static final String WORKSPACE_RESUME = "RESUME";
    public static final String WORKSPACE_GENERAL = "GENERAL";
    /** 兼容历史数据与旧调用，新逻辑优先使用 {@link #WORKSPACE_GENERAL} */
    public static final String WORKSPACE_CHAT = "CHAT";

    private static final Set<String> KNOWN_WORKSPACE_TYPES = Set.of(
            WORKSPACE_JD_PREP,
            WORKSPACE_INTERVIEW,
            WORKSPACE_MARKET,
            WORKSPACE_RESUME,
            WORKSPACE_GENERAL
    );

    private final AgentSessionMapper agentSessionMapper;
    private final AgentMessageMapper agentMessageMapper;
    private final AgentTaskStateMapper agentTaskStateMapper;
    private final ObjectMapper objectMapper;

    public WorkspaceSessionRepository(
            AgentSessionMapper agentSessionMapper,
            AgentMessageMapper agentMessageMapper,
            AgentTaskStateMapper agentTaskStateMapper,
            ObjectMapper objectMapper
    ) {
        this.agentSessionMapper = agentSessionMapper;
        this.agentMessageMapper = agentMessageMapper;
        this.agentTaskStateMapper = agentTaskStateMapper;
        this.objectMapper = objectMapper;
    }

    public static String normalizeWorkspaceType(String workspaceType) {
        if (workspaceType == null || workspaceType.isBlank()) {
            return WORKSPACE_GENERAL;
        }
        String upper = workspaceType.trim().toUpperCase();
        if (WORKSPACE_CHAT.equals(upper)) {
            return WORKSPACE_GENERAL;
        }
        return upper;
    }

    public static String displayWorkspaceType(String workspaceType) {
        return normalizeWorkspaceType(workspaceType);
    }

    public AgentSessionEntity findActiveJdPrepSession(Long userId, String jdId) {
        return agentSessionMapper.selectOne(
                new LambdaQueryWrapper<AgentSessionEntity>()
                        .eq(AgentSessionEntity::getUserId, userId)
                        .eq(AgentSessionEntity::getJdId, jdId)
                        .eq(AgentSessionEntity::getWorkspaceType, WORKSPACE_JD_PREP)
                        .eq(AgentSessionEntity::getStatus, STATUS_ACTIVE)
                        .orderByDesc(AgentSessionEntity::getCreatedAt)
                        .last("LIMIT 1")
        );
    }

    public AgentSessionEntity findReusableWorkspace(Long userId, String workspaceType, Map<String, Object> contextMetadata) {
        String normalizedType = normalizeWorkspaceType(workspaceType);
        if (WORKSPACE_JD_PREP.equals(normalizedType)) {
            String jdId = stringMeta(contextMetadata, "jdId");
            return jdId == null ? null : findActiveJdPrepSession(userId, jdId);
        }

        LambdaQueryWrapper<AgentSessionEntity> wrapper = new LambdaQueryWrapper<AgentSessionEntity>()
                .eq(AgentSessionEntity::getUserId, userId)
                .eq(AgentSessionEntity::getWorkspaceType, normalizedType)
                .eq(AgentSessionEntity::getStatus, STATUS_ACTIVE);

        boolean hasStableKey = switch (normalizedType) {
            case WORKSPACE_MARKET -> appendMarketReuseKey(wrapper, contextMetadata);
            case WORKSPACE_INTERVIEW -> appendInterviewReuseKey(wrapper, contextMetadata);
            case WORKSPACE_RESUME -> appendResumeReuseKey(wrapper, contextMetadata);
            default -> false;
        };
        if (!hasStableKey) {
            return null;
        }
        return agentSessionMapper.selectOne(wrapper.orderByDesc(AgentSessionEntity::getUpdatedAt).last("LIMIT 1"));
    }

    private boolean appendMarketReuseKey(LambdaQueryWrapper<AgentSessionEntity> wrapper, Map<String, Object> metadata) {
        boolean hasCity = appendJsonTextEquals(wrapper, "city", stringMeta(metadata, "city"));
        boolean hasRole = appendJsonTextEquals(wrapper, "role", stringMeta(metadata, "role"));
        boolean hasYears = appendJsonTextEquals(wrapper, "years", stringMeta(metadata, "years"));
        return hasCity && hasRole && hasYears;
    }

    private boolean appendInterviewReuseKey(LambdaQueryWrapper<AgentSessionEntity> wrapper, Map<String, Object> metadata) {
        String questionId = stringMeta(metadata, "questionId");
        if (questionId != null) {
            return appendJsonTextEquals(wrapper, "questionId", questionId);
        }
        String kbQuery = stringMeta(metadata, "kbQuery");
        if (kbQuery != null) {
            return appendJsonTextEquals(wrapper, "kbQuery", kbQuery);
        }
        return appendJsonTextEquals(wrapper, "questionText", stringMeta(metadata, "questionText"));
    }

    private boolean appendResumeReuseKey(LambdaQueryWrapper<AgentSessionEntity> wrapper, Map<String, Object> metadata) {
        String artifactId = stringMeta(metadata, "artifactId");
        if (artifactId != null) {
            return appendJsonTextEquals(wrapper, "artifactId", artifactId);
        }
        boolean hasRefType = appendJsonTextEquals(wrapper, "refType", stringMeta(metadata, "refType"));
        boolean hasRefId = appendJsonTextEquals(wrapper, "refId", stringMeta(metadata, "refId"));
        return hasRefType && hasRefId;
    }

    private boolean appendJsonTextEquals(
            LambdaQueryWrapper<AgentSessionEntity> wrapper,
            String field,
            String value
    ) {
        if (value == null || value.isBlank()) {
            return false;
        }
        wrapper.apply("workspace_metadata ->> '" + field + "' = {0}", value.trim());
        return true;
    }

    private String stringMeta(Map<String, Object> metadata, String key) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    public AgentSessionEntity requireSession(Long userId, String sessionId) {
        AgentSessionEntity session = agentSessionMapper.selectOne(
                new LambdaQueryWrapper<AgentSessionEntity>()
                        .eq(AgentSessionEntity::getSessionId, sessionId)
                        .last("LIMIT 1")
        );
        if (session == null) {
            throw new BizException(404, "工作空间不存在");
        }
        if (!userId.equals(session.getUserId())) {
            throw new BizException(403, "无权访问该工作空间");
        }
        return session;
    }

    public AgentSessionEntity getSessionIfExists(Long userId, String sessionId) {
        try {
            return requireSession(userId, sessionId);
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public AgentSessionEntity createJdPrepSession(
            Long userId,
            String jdId,
            String jdSnapshotJson,
            String title
    ) {
        String sessionId = "WS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        OffsetDateTime now = OffsetDateTime.now();

        AgentSessionEntity session = new AgentSessionEntity();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setStatus(STATUS_ACTIVE);
        session.setTaskType(WORKSPACE_JD_PREP);
        session.setWorkspaceType(WORKSPACE_JD_PREP);
        session.setJdId(jdId);
        session.setJdSnapshot(jdSnapshotJson);
        session.setTitle(title);
        session.setToolCallCount(0);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        agentSessionMapper.insert(session);

        insertTaskState(userId, session.getId(), WORKSPACE_JD_PREP, now);

        return session;
    }

    @Transactional
    public AgentSessionEntity createWorkspace(
            Long userId,
            String workspaceType,
            String title,
            String goalText,
            Map<String, Object> contextMetadata
    ) {
        String normalizedType = normalizeWorkspaceType(workspaceType);
        validateWorkspaceType(normalizedType);

        String sessionId = "WS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        OffsetDateTime now = OffsetDateTime.now();

        AgentSessionEntity session = new AgentSessionEntity();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setStatus(STATUS_ACTIVE);
        session.setTaskType(normalizedType);
        session.setWorkspaceType(normalizedType);
        session.setTitle(title);
        session.setGoalText(resolveGoalText(goalText, title));
        session.setWorkspaceMetadata(serializeMetadata(contextMetadata));
        session.setToolCallCount(0);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);

        applyContextMetadata(session, normalizedType, contextMetadata);

        agentSessionMapper.insert(session);
        insertTaskState(userId, session.getId(), normalizedType, now);
        return session;
    }

    @Transactional
    public AgentMessageEntity appendMessage(
            Long userId,
            AgentSessionEntity session,
            String role,
            String content,
            String messageType,
            String metadataJson,
            Integer sequenceNo
    ) {
        if (content == null || content.isBlank()) {
            throw new BizException(400, "消息内容不能为空");
        }
        int seq = sequenceNo != null ? sequenceNo : nextSequenceNo(session.getId(), userId);

        AgentMessageEntity message = new AgentMessageEntity();
        message.setSessionId(session.getId());
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setMessageType(messageType);
        message.setSequenceNo(seq);
        message.setMetadata(metadataJson);
        OffsetDateTime now = OffsetDateTime.now();
        message.setCreatedAt(now);
        agentMessageMapper.insert(message);

        agentSessionMapper.update(null, new LambdaUpdateWrapper<AgentSessionEntity>()
                .eq(AgentSessionEntity::getId, session.getId())
                .eq(AgentSessionEntity::getUserId, userId)
                .set(AgentSessionEntity::getUpdatedAt, now));
        return message;
    }

    public List<AgentMessageEntity> listMessages(Long internalSessionId, Long userId, Integer afterSeq, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        LambdaQueryWrapper<AgentMessageEntity> wrapper = new LambdaQueryWrapper<AgentMessageEntity>()
                .eq(AgentMessageEntity::getSessionId, internalSessionId)
                .eq(AgentMessageEntity::getUserId, userId)
                .orderByAsc(AgentMessageEntity::getSequenceNo)
                .last("LIMIT " + safeLimit);
        if (afterSeq != null && afterSeq > 0) {
            wrapper.gt(AgentMessageEntity::getSequenceNo, afterSeq);
        }
        return agentMessageMapper.selectList(wrapper);
    }

    private void validateWorkspaceType(String normalizedType) {
        if (!KNOWN_WORKSPACE_TYPES.contains(normalizedType)) {
            throw new BizException(400, "不支持的工作空间类型: " + normalizedType);
        }
    }

    private void applyContextMetadata(
            AgentSessionEntity session,
            String normalizedType,
            Map<String, Object> contextMetadata
    ) {
        if (contextMetadata == null || contextMetadata.isEmpty()) {
            return;
        }
        if (WORKSPACE_JD_PREP.equals(normalizedType)) {
            Object jdId = contextMetadata.get("jdId");
            if (jdId != null && !String.valueOf(jdId).isBlank()) {
                session.setJdId(String.valueOf(jdId).trim());
            }
            Object jdSnapshot = contextMetadata.get("jdSnapshot");
            if (jdSnapshot instanceof String snapshotJson && !snapshotJson.isBlank()) {
                session.setJdSnapshot(snapshotJson);
            } else if (jdSnapshot instanceof Map<?, ?> snapshotMap && !snapshotMap.isEmpty()) {
                session.setJdSnapshot(serializeMetadata(snapshotMap));
            }
        }
    }

    private String resolveGoalText(String goalText, String title) {
        if (goalText != null && !goalText.isBlank()) {
            return goalText.trim();
        }
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        return null;
    }

    private String serializeMetadata(Map<?, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            throw new BizException(400, "工作空间 metadata 序列化失败");
        }
    }

    private void insertTaskState(Long userId, Long internalSessionId, String taskType, OffsetDateTime now) {
        AgentTaskStateEntity taskState = new AgentTaskStateEntity();
        taskState.setSessionId(internalSessionId);
        taskState.setUserId(userId);
        taskState.setTaskType(taskType);
        taskState.setCurrentStep(0);
        taskState.setTotalSteps(0);
        taskState.setStateData("{}");
        taskState.setStatus("RUNNING");
        taskState.setCreatedAt(now);
        taskState.setUpdatedAt(now);
        agentTaskStateMapper.insert(taskState);
    }

    private int nextSequenceNo(Long internalSessionId, Long userId) {
        AgentMessageEntity last = agentMessageMapper.selectOne(
                new LambdaQueryWrapper<AgentMessageEntity>()
                        .eq(AgentMessageEntity::getSessionId, internalSessionId)
                        .eq(AgentMessageEntity::getUserId, userId)
                        .orderByDesc(AgentMessageEntity::getSequenceNo)
                        .last("LIMIT 1")
        );
        return last == null ? 1 : last.getSequenceNo() + 1;
    }

    public static final String STATUS_ACTIVE = "ACTIVE";
}
