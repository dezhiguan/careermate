package com.careermate.agent.session;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.careermate.agent.dto.AgentMessageResponse;
import com.careermate.agent.dto.AgentSessionCreateResponse;
import com.careermate.agent.dto.AgentSessionResponse;
import com.careermate.agent.dto.AgentTraceResponse;
import com.careermate.common.exception.BizException;
import com.careermate.mapper.AgentMessageMapper;
import com.careermate.mapper.AgentSessionMapper;
import com.careermate.mapper.AgentTaskStateMapper;
import com.careermate.mapper.AgentToolCallMapper;
import com.careermate.model.entity.AgentMessageEntity;
import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.model.entity.AgentTaskStateEntity;
import com.careermate.model.entity.AgentToolCallEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AgentSessionServiceImpl implements AgentSessionService {

    private final AgentSessionMapper agentSessionMapper;
    private final AgentMessageMapper agentMessageMapper;
    private final AgentToolCallMapper agentToolCallMapper;
    private final AgentTaskStateMapper agentTaskStateMapper;

    public AgentSessionServiceImpl(
            AgentSessionMapper agentSessionMapper,
            AgentMessageMapper agentMessageMapper,
            AgentToolCallMapper agentToolCallMapper,
            AgentTaskStateMapper agentTaskStateMapper
    ) {
        this.agentSessionMapper = agentSessionMapper;
        this.agentMessageMapper = agentMessageMapper;
        this.agentToolCallMapper = agentToolCallMapper;
        this.agentTaskStateMapper = agentTaskStateMapper;
    }

    @Override
    @Transactional
    public AgentSessionCreateResponse createSession(Long userId) {
        String sessionId = "s_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        OffsetDateTime now = OffsetDateTime.now();

        AgentSessionEntity session = new AgentSessionEntity();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setStatus("ACTIVE");
        session.setTitle("新会话");
        session.setToolCallCount(0);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        agentSessionMapper.insert(session);

        AgentTaskStateEntity taskState = new AgentTaskStateEntity();
        taskState.setSessionId(session.getId());
        taskState.setUserId(userId);
        taskState.setTaskType("CHAT");
        taskState.setCurrentStep(0);
        taskState.setTotalSteps(0);
        taskState.setStateData("{}");
        taskState.setStatus("RUNNING");
        taskState.setCreatedAt(now);
        taskState.setUpdatedAt(now);
        agentTaskStateMapper.insert(taskState);

        return AgentSessionCreateResponse.builder().sessionId(sessionId).build();
    }

    @Override
    public AgentSessionResponse getSession(Long userId, String sessionId) {
        AgentSessionEntity session = getSessionByUser(userId, sessionId);
        List<AgentMessageEntity> messages = agentMessageMapper.selectList(
                new LambdaQueryWrapper<AgentMessageEntity>()
                        .eq(AgentMessageEntity::getSessionId, session.getId())
                        .eq(AgentMessageEntity::getUserId, userId)
                        .orderByAsc(AgentMessageEntity::getSequenceNo)
        );

        List<AgentMessageResponse> messageResponses = messages.stream().map(m -> AgentMessageResponse.builder()
                .id(m.getId())
                .role(m.getRole())
                .content(m.getContent())
                .messageType(m.getMessageType())
                .sequenceNo(m.getSequenceNo())
                .createdAt(m.getCreatedAt())
                .build()).toList();

        return AgentSessionResponse.builder()
                .sessionId(session.getSessionId())
                .status(session.getStatus())
                .title(session.getTitle())
                .intent(session.getIntent())
                .taskType(session.getTaskType())
                .totalLatencyMs(session.getTotalLatencyMs())
                .messages(messageResponses)
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }

    @Override
    public List<AgentTraceResponse> getTrace(Long userId, String sessionId) {
        AgentSessionEntity session = getSessionByUser(userId, sessionId);
        List<AgentToolCallEntity> traces = agentToolCallMapper.selectList(
                new LambdaQueryWrapper<AgentToolCallEntity>()
                        .eq(AgentToolCallEntity::getSessionId, session.getId())
                        .eq(AgentToolCallEntity::getUserId, userId)
                        .orderByAsc(AgentToolCallEntity::getCreatedAt)
        );

        return traces.stream().map(t -> AgentTraceResponse.builder()
                .id(t.getId())
                .type(t.getToolName())
                .toolName(t.getToolName())
                .status(t.getStatus())
                .requestSummary(t.getRequestParamsSummary())
                .responseSummary(t.getResponseSummary())
                .latencyMs(t.getLatencyMs())
                .fallbackUsed(t.getFallbackUsed())
                .errorCode(t.getErrorCode())
                .createdAt(t.getCreatedAt())
                .build()).toList();
    }

    @Override
    @Transactional
    public AgentMessageEntity appendMessage(Long userId, String sessionId, String role, String content, String messageType) {
        if (content == null || content.isBlank()) {
            throw new BizException(400, "消息内容不能为空");
        }
        AgentSessionEntity session = getSessionByUser(userId, sessionId);
        AgentMessageEntity last = agentMessageMapper.selectOne(
                new LambdaQueryWrapper<AgentMessageEntity>()
                        .eq(AgentMessageEntity::getSessionId, session.getId())
                        .eq(AgentMessageEntity::getUserId, userId)
                        .orderByDesc(AgentMessageEntity::getSequenceNo)
                        .last("LIMIT 1")
        );
        int next = last == null ? 1 : last.getSequenceNo() + 1;

        AgentMessageEntity message = new AgentMessageEntity();
        message.setSessionId(session.getId());
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setMessageType(messageType);
        message.setSequenceNo(next);
        message.setCreatedAt(OffsetDateTime.now());
        agentMessageMapper.insert(message);
        return message;
    }

    @Override
    @Transactional
    public void recordTrace(Long userId, String sessionId, String toolName, String requestSummary, String responseSummary,
                            String status, Long latencyMs, String errorCode) {
        AgentSessionEntity session = getSessionByUser(userId, sessionId);
        AgentToolCallEntity trace = new AgentToolCallEntity();
        trace.setSessionId(session.getId());
        trace.setUserId(userId);
        trace.setToolName(toolName);
        trace.setToolLayer("RUNTIME");
        trace.setRequestParamsSummary(safeJson(requestSummary));
        trace.setResponseSummary(safeJson(responseSummary));
        trace.setStatus(status);
        trace.setLatencyMs(latencyMs);
        trace.setFallbackUsed(false);
        trace.setErrorCode(errorCode);
        trace.setCreatedAt(OffsetDateTime.now());
        agentToolCallMapper.insert(trace);
    }

    @Override
    @Transactional
    public void markCompleted(Long userId, String sessionId, Long totalLatencyMs) {
        AgentSessionEntity session = getSessionByUser(userId, sessionId);
        OffsetDateTime now = OffsetDateTime.now();

        agentSessionMapper.update(null, new LambdaUpdateWrapper<AgentSessionEntity>()
                .eq(AgentSessionEntity::getId, session.getId())
                .eq(AgentSessionEntity::getUserId, userId)
                .set(AgentSessionEntity::getStatus, "COMPLETED")
                .set(AgentSessionEntity::getTotalLatencyMs, totalLatencyMs)
                .set(AgentSessionEntity::getUpdatedAt, now));

        agentTaskStateMapper.update(null, new LambdaUpdateWrapper<AgentTaskStateEntity>()
                .eq(AgentTaskStateEntity::getSessionId, session.getId())
                .eq(AgentTaskStateEntity::getUserId, userId)
                .set(AgentTaskStateEntity::getStatus, "COMPLETED")
                .set(AgentTaskStateEntity::getUpdatedAt, now));
    }

    @Override
    @Transactional
    public void markError(Long userId, String sessionId, String errorCode) {
        AgentSessionEntity session = getSessionByUser(userId, sessionId);
        OffsetDateTime now = OffsetDateTime.now();

        agentSessionMapper.update(null, new LambdaUpdateWrapper<AgentSessionEntity>()
                .eq(AgentSessionEntity::getId, session.getId())
                .eq(AgentSessionEntity::getUserId, userId)
                .set(AgentSessionEntity::getStatus, "ERROR")
                .set(AgentSessionEntity::getErrorCode, errorCode)
                .set(AgentSessionEntity::getUpdatedAt, now));

        agentTaskStateMapper.update(null, new LambdaUpdateWrapper<AgentTaskStateEntity>()
                .eq(AgentTaskStateEntity::getSessionId, session.getId())
                .eq(AgentTaskStateEntity::getUserId, userId)
                .set(AgentTaskStateEntity::getStatus, "FAILED")
                .set(AgentTaskStateEntity::getUpdatedAt, now));
    }

    private AgentSessionEntity getSessionByUser(Long userId, String sessionId) {
        AgentSessionEntity session = agentSessionMapper.selectOne(
                new LambdaQueryWrapper<AgentSessionEntity>()
                        .eq(AgentSessionEntity::getSessionId, sessionId)
                        .eq(AgentSessionEntity::getUserId, userId)
                        .last("LIMIT 1")
        );
        if (session == null) {
            throw new BizException(404, "会话不存在");
        }
        return session;
    }

    private String safeJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return "{}";
        }
        return raw;
    }
}
