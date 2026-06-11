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
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class WorkspaceSessionRepository {

    public static final String WORKSPACE_JD_PREP = "JD_PREP";
    public static final String WORKSPACE_CHAT = "CHAT";
    public static final String STATUS_ACTIVE = "ACTIVE";

    private final AgentSessionMapper agentSessionMapper;
    private final AgentMessageMapper agentMessageMapper;
    private final AgentTaskStateMapper agentTaskStateMapper;

    public WorkspaceSessionRepository(
            AgentSessionMapper agentSessionMapper,
            AgentMessageMapper agentMessageMapper,
            AgentTaskStateMapper agentTaskStateMapper
    ) {
        this.agentSessionMapper = agentSessionMapper;
        this.agentMessageMapper = agentMessageMapper;
        this.agentTaskStateMapper = agentTaskStateMapper;
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
        session.setWorkspaceType(WORKSPACE_JD_PREP);
        session.setJdId(jdId);
        session.setJdSnapshot(jdSnapshotJson);
        session.setTitle(title);
        session.setToolCallCount(0);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        agentSessionMapper.insert(session);

        AgentTaskStateEntity taskState = new AgentTaskStateEntity();
        taskState.setSessionId(session.getId());
        taskState.setUserId(userId);
        taskState.setTaskType(WORKSPACE_JD_PREP);
        taskState.setCurrentStep(0);
        taskState.setTotalSteps(0);
        taskState.setStateData("{}");
        taskState.setStatus("RUNNING");
        taskState.setCreatedAt(now);
        taskState.setUpdatedAt(now);
        agentTaskStateMapper.insert(taskState);

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
}
