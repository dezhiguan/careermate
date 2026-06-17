package com.careermate.workspace.pending;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.careermate.agent.session.AgentSessionService;
import com.careermate.common.exception.BizException;
import com.careermate.mapper.AgentPendingActionMapper;
import com.careermate.model.entity.AgentPendingActionEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class PendingActionService {

    public static final Duration DEFAULT_TTL = Duration.ofMinutes(15);
    public static final String TRACE_HITL_PENDING = "HITL_PENDING";
    public static final String TRACE_HITL_CONFIRMED = "HITL_CONFIRMED";
    public static final String TRACE_HITL_CANCELLED = "HITL_CANCELLED";

    private final AgentPendingActionMapper pendingActionMapper;
    private final AgentSessionService agentSessionService;
    private final ObjectMapper objectMapper;

    public PendingActionService(
            AgentPendingActionMapper pendingActionMapper,
            AgentSessionService agentSessionService,
            ObjectMapper objectMapper
    ) {
        this.pendingActionMapper = pendingActionMapper;
        this.agentSessionService = agentSessionService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PendingActionCreateResult createGenerateResumePendingAction(
            Long userId,
            String sessionId,
            String jdId
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        String actionId = "PA-" + UUID.randomUUID().toString().replace("-", "");
        String safeJdId = normalizeJdId(jdId);

        AgentPendingActionEntity entity = new AgentPendingActionEntity();
        entity.setActionId(actionId);
        entity.setUserId(userId);
        entity.setSessionId(sessionId);
        entity.setActionType(PendingActionType.GENERATE_RESUME_FROM_JD.name());
        entity.setStatus(PendingActionStatus.PENDING.name());
        entity.setPayload(writePayload(Map.of(
                "jdId", safeJdId == null ? "" : safeJdId,
                "hasJdId", safeJdId != null && !safeJdId.isBlank()
        )));
        entity.setExpiresAt(now.plus(DEFAULT_TTL));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        pendingActionMapper.insert(entity);

        recordTrace(userId, sessionId, TRACE_HITL_PENDING, safeTraceRequest(entity), safeTracePendingResponse(entity));

        return new PendingActionCreateResult(
                actionId,
                buildConfirmCard(entity),
                entity.getExpiresAt()
        );
    }

    @Transactional
    public ConfirmedPendingAction confirm(Long userId, String sessionId, String actionId) {
        AgentPendingActionEntity entity = requireOwnedAction(userId, sessionId, actionId);
        expireIfNeeded(entity);
        if (PendingActionStatus.CONFIRMED.name().equals(entity.getStatus())) {
            if (entity.getConsumedAt() != null) {
                throw new BizException(409, "该确认已使用，请重新发起生成");
            }
            return toConfirmed(entity);
        }
        if (!PendingActionStatus.PENDING.name().equals(entity.getStatus())) {
            throw new BizException(409, "该操作已无法确认");
        }
        OffsetDateTime now = OffsetDateTime.now();
        pendingActionMapper.update(null, new UpdateWrapper<AgentPendingActionEntity>()
                .eq("id", entity.getId())
                .set("status", PendingActionStatus.CONFIRMED.name())
                .set("updated_at", now));
        entity.setStatus(PendingActionStatus.CONFIRMED.name());
        entity.setUpdatedAt(now);

        recordTrace(userId, sessionId, TRACE_HITL_CONFIRMED, safeTraceRequest(entity), safeTraceConfirmResponse(entity));
        return toConfirmed(entity);
    }

    @Transactional
    public void cancel(Long userId, String sessionId, String actionId) {
        AgentPendingActionEntity entity = requireOwnedAction(userId, sessionId, actionId);
        if (markExpiredIfNeeded(entity)) {
            return;
        }
        if (PendingActionStatus.CANCELLED.name().equals(entity.getStatus())) {
            return;
        }
        if (PendingActionStatus.CONFIRMED.name().equals(entity.getStatus())) {
            throw new BizException(409, "已确认的操作无法取消");
        }
        OffsetDateTime now = OffsetDateTime.now();
        pendingActionMapper.update(null, new UpdateWrapper<AgentPendingActionEntity>()
                .eq("id", entity.getId())
                .set("status", PendingActionStatus.CANCELLED.name())
                .set("updated_at", now));
        entity.setStatus(PendingActionStatus.CANCELLED.name());

        recordTrace(userId, sessionId, TRACE_HITL_CANCELLED, safeTraceRequest(entity), safeTraceCancelResponse(entity));
    }

    @Transactional(readOnly = true)
    public AgentPendingActionEntity getActive(Long userId, String sessionId, String actionId) {
        AgentPendingActionEntity entity = requireOwnedAction(userId, sessionId, actionId);
        expireIfNeeded(entity);
        return entity;
    }

    @Transactional
    public void validateAndConsumeConfirmed(
            Long userId,
            String sessionId,
            String pendingActionId,
            String jdId
    ) {
        AgentPendingActionEntity entity = requireOwnedAction(userId, sessionId, pendingActionId);
        expireIfNeeded(entity);
        if (!PendingActionType.GENERATE_RESUME_FROM_JD.name().equals(entity.getActionType())) {
            throw new BizException(400, "待确认动作类型不匹配");
        }
        if (!PendingActionStatus.CONFIRMED.name().equals(entity.getStatus())) {
            throw new BizException(403, "简历生成尚未确认或已失效");
        }
        if (entity.getConsumedAt() != null) {
            throw new BizException(409, "该确认已使用，请重新发起生成");
        }
        String payloadJdId = readPayloadJdId(entity);
        String effectiveJdId = normalizeJdId(jdId);
        if (payloadJdId != null && effectiveJdId != null && !payloadJdId.equals(effectiveJdId)) {
            throw new BizException(400, "待确认动作与 JD 不匹配");
        }

        OffsetDateTime now = OffsetDateTime.now();
        pendingActionMapper.update(null, new UpdateWrapper<AgentPendingActionEntity>()
                .eq("id", entity.getId())
                .set("consumed_at", now)
                .set("updated_at", now));
    }

    private AgentPendingActionEntity requireOwnedAction(Long userId, String sessionId, String actionId) {
        if (actionId == null || actionId.isBlank()) {
            throw new BizException(400, "actionId 不能为空");
        }
        AgentPendingActionEntity entity = pendingActionMapper.selectOne(
                new LambdaQueryWrapper<AgentPendingActionEntity>()
                        .eq(AgentPendingActionEntity::getActionId, actionId.trim())
                        .last("LIMIT 1")
        );
        if (entity == null) {
            throw new BizException(404, "待确认操作不存在");
        }
        if (!userId.equals(entity.getUserId()) || !sessionId.equals(entity.getSessionId())) {
            throw new BizException(403, "无权操作该待确认动作");
        }
        return entity;
    }

    private void expireIfNeeded(AgentPendingActionEntity entity) {
        if (markExpiredIfNeeded(entity)) {
            throw new BizException(410, "待确认操作已过期");
        }
        if (PendingActionStatus.CANCELLED.name().equals(entity.getStatus())) {
            throw new BizException(409, "该操作已取消");
        }
    }

    private boolean markExpiredIfNeeded(AgentPendingActionEntity entity) {
        if (entity == null) {
            return false;
        }
        if (PendingActionStatus.EXPIRED.name().equals(entity.getStatus())) {
            return true;
        }
        if (entity.getExpiresAt() != null && OffsetDateTime.now().isAfter(entity.getExpiresAt())) {
            pendingActionMapper.update(null, new UpdateWrapper<AgentPendingActionEntity>()
                    .eq("id", entity.getId())
                    .set("status", PendingActionStatus.EXPIRED.name())
                    .set("updated_at", OffsetDateTime.now()));
            entity.setStatus(PendingActionStatus.EXPIRED.name());
            return true;
        }
        return false;
    }

    private ConfirmedPendingAction toConfirmed(AgentPendingActionEntity entity) {
        return new ConfirmedPendingAction(entity.getActionId(), readPayloadJdId(entity));
    }

    private String readPayloadJdId(AgentPendingActionEntity entity) {
        Map<String, Object> payload = parsePayload(entity.getPayload());
        Object jdId = payload.get("jdId");
        if (jdId == null) {
            return null;
        }
        String text = String.valueOf(jdId).trim();
        return text.isBlank() ? null : text;
    }

    private Map<String, Object> parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    Map<String, Object> buildConfirmCard(AgentPendingActionEntity entity) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("type", "CONFIRM_ACTION");
        card.put("title", "确认按 JD 生成定制简历");
        card.put("summary", "将基于当前工作空间中的 JD 生成新的简历版本并保存。"
                + "此操作会写入简历版本，请确认后继续。");
        card.put("riskLabel", "高风险写入");
        card.put("actionId", entity.getActionId());
        card.put("expiresAt", formatExpiresAt(entity.getExpiresAt()));
        card.put("actions", List.of(
                Map.of(
                        "label", "确认生成",
                        "action", "CONFIRM_PENDING_ACTION",
                        "payload", Map.of("actionId", entity.getActionId())
                ),
                Map.of(
                        "label", "取消",
                        "action", "CANCEL_PENDING_ACTION",
                        "payload", Map.of("actionId", entity.getActionId())
                )
        ));
        return card;
    }

    public Map<String, Object> buildCancelledCard(String actionId) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("type", "ACTION_CANCELLED");
        card.put("title", "已取消简历生成");
        card.put("summary", "未启动按 JD 生成简历流程。如需继续，可再次点击生成。");
        card.put("actionId", actionId);
        return card;
    }

    private static String formatExpiresAt(OffsetDateTime expiresAt) {
        if (expiresAt == null) {
            return "";
        }
        return expiresAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static String normalizeJdId(String jdId) {
        if (jdId == null || jdId.isBlank()) {
            return null;
        }
        return jdId.trim();
    }

    private void recordTrace(
            Long userId,
            String sessionId,
            String traceName,
            String requestSummary,
            String responseSummary
    ) {
        agentSessionService.recordTrace(
                userId,
                sessionId,
                traceName,
                requestSummary,
                responseSummary,
                "SUCCESS",
                null,
                null
        );
    }

    private String safeTraceRequest(AgentPendingActionEntity entity) {
        return writePayload(Map.of(
                "actionType", entity.getActionType(),
                "actionId", entity.getActionId(),
                "hasJdId", readPayloadJdId(entity) != null
        ));
    }

    private String safeTracePendingResponse(AgentPendingActionEntity entity) {
        return writePayload(Map.of(
                "status", entity.getStatus(),
                "expiresAt", formatExpiresAt(entity.getExpiresAt())
        ));
    }

    private String safeTraceConfirmResponse(AgentPendingActionEntity entity) {
        return writePayload(Map.of(
                "status", PendingActionStatus.CONFIRMED.name(),
                "actionId", entity.getActionId()
        ));
    }

    private String safeTraceCancelResponse(AgentPendingActionEntity entity) {
        return writePayload(Map.of(
                "status", PendingActionStatus.CANCELLED.name(),
                "actionId", entity.getActionId()
        ));
    }
}
