package com.careermate.workspace.pending;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.agent.session.AgentSessionService;
import com.careermate.common.exception.BizException;
import com.careermate.mapper.AgentPendingActionMapper;
import com.careermate.model.entity.AgentPendingActionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PendingActionServiceTest {

    @Mock
    private AgentPendingActionMapper pendingActionMapper;
    @Mock
    private AgentSessionService agentSessionService;

    private PendingActionService service;

    @BeforeEach
    void setUp() {
        service = new PendingActionService(pendingActionMapper, agentSessionService, new ObjectMapper());
        doReturn(1).when(pendingActionMapper).update(any(), any());
    }

    @Test
    void createGenerateResumePendingActionReturnsConfirmCard() {
        PendingActionCreateResult result = service.createGenerateResumePendingAction(1L, "WS-1", "doc-1");

        assertNotNull(result.actionId());
        assertTrue(result.actionId().startsWith("PA-"));
        assertEquals("CONFIRM_ACTION", result.confirmCard().get("type"));
        assertEquals("高风险写入", result.confirmCard().get("riskLabel"));
        assertNotNull(result.expiresAt());

        ArgumentCaptor<AgentPendingActionEntity> captor = ArgumentCaptor.forClass(AgentPendingActionEntity.class);
        verify(pendingActionMapper).insert(captor.capture());
        assertEquals(PendingActionStatus.PENDING.name(), captor.getValue().getStatus());
        verify(agentSessionService).recordTrace(eq(1L), eq("WS-1"), eq(PendingActionService.TRACE_HITL_PENDING),
                any(), any(), eq("SUCCESS"), eq(null), eq(null));
    }

    @Test
    void confirmMarksConfirmedAndReturnsJdId() {
        AgentPendingActionEntity entity = pendingEntity("PA-1", 1L, "WS-1", PendingActionStatus.PENDING,
                OffsetDateTime.now().plusMinutes(10), "{\"jdId\":\"doc-1\",\"hasJdId\":true}");
        when(pendingActionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        ConfirmedPendingAction confirmed = service.confirm(1L, "WS-1", "PA-1");

        assertEquals("PA-1", confirmed.actionId());
        assertEquals("doc-1", confirmed.jdId());
        verify(agentSessionService).recordTrace(eq(1L), eq("WS-1"), eq(PendingActionService.TRACE_HITL_CONFIRMED),
                any(), any(), eq("SUCCESS"), eq(null), eq(null));
    }

    @Test
    void cancelMarksCancelled() {
        AgentPendingActionEntity entity = pendingEntity("PA-2", 1L, "WS-1", PendingActionStatus.PENDING,
                OffsetDateTime.now().plusMinutes(10), "{\"jdId\":\"doc-1\"}");
        when(pendingActionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        service.cancel(1L, "WS-1", "PA-2");

        verify(agentSessionService).recordTrace(eq(1L), eq("WS-1"), eq(PendingActionService.TRACE_HITL_CANCELLED),
                any(), any(), eq("SUCCESS"), eq(null), eq(null));
    }

    @Test
    void expiredPendingActionCannotConfirm() {
        AgentPendingActionEntity entity = pendingEntity("PA-3", 1L, "WS-1", PendingActionStatus.PENDING,
                OffsetDateTime.now().minusMinutes(1), "{\"jdId\":\"doc-1\"}");
        when(pendingActionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        BizException ex = assertThrows(BizException.class, () -> service.confirm(1L, "WS-1", "PA-3"));
        assertEquals(410, ex.getCode());
    }

    @Test
    void mismatchedUserCannotConfirm() {
        AgentPendingActionEntity entity = pendingEntity("PA-4", 1L, "WS-1", PendingActionStatus.PENDING,
                OffsetDateTime.now().plusMinutes(10), "{\"jdId\":\"doc-1\"}");
        when(pendingActionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        BizException ex = assertThrows(BizException.class, () -> service.confirm(2L, "WS-1", "PA-4"));
        assertEquals(403, ex.getCode());
    }

    @Test
    void validateAndConsumeRequiresConfirmedPendingAction() {
        AgentPendingActionEntity entity = pendingEntity("PA-5", 1L, "WS-1", PendingActionStatus.PENDING,
                OffsetDateTime.now().plusMinutes(10), "{\"jdId\":\"doc-1\"}");
        when(pendingActionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        BizException ex = assertThrows(BizException.class,
                () -> service.validateAndConsumeConfirmed(1L, "WS-1", "PA-5", "doc-1"));
        assertEquals(403, ex.getCode());
    }

    @Test
    void validateAndConsumeConfirmedAllowsOnce() {
        AgentPendingActionEntity entity = pendingEntity("PA-6", 1L, "WS-1", PendingActionStatus.CONFIRMED,
                OffsetDateTime.now().plusMinutes(10), "{\"jdId\":\"doc-1\"}");
        when(pendingActionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity);

        service.validateAndConsumeConfirmed(1L, "WS-1", "PA-6", "doc-1");

        verify(pendingActionMapper).update(any(), any());
    }

    @Test
    void confirmCardDoesNotExposeJdContent() {
        AgentPendingActionEntity entity = pendingEntity("PA-7", 1L, "WS-1", PendingActionStatus.PENDING,
                OffsetDateTime.now().plusMinutes(10), "{\"jdId\":\"doc-1\",\"hasJdId\":true}");

        Map<String, Object> card = service.buildConfirmCard(entity);

        assertEquals("CONFIRM_ACTION", card.get("type"));
        assertTrue(card.toString().contains("actionId"));
        assertTrue(!card.containsKey("jdContent"));
        assertTrue(!card.containsKey("resumeContent"));
    }

    private static AgentPendingActionEntity pendingEntity(
            String actionId,
            Long userId,
            String sessionId,
            PendingActionStatus status,
            OffsetDateTime expiresAt,
            String payload
    ) {
        AgentPendingActionEntity entity = new AgentPendingActionEntity();
        entity.setId(1L);
        entity.setActionId(actionId);
        entity.setUserId(userId);
        entity.setSessionId(sessionId);
        entity.setActionType(PendingActionType.GENERATE_RESUME_FROM_JD.name());
        entity.setStatus(status.name());
        entity.setPayload(payload);
        entity.setExpiresAt(expiresAt);
        return entity;
    }
}
