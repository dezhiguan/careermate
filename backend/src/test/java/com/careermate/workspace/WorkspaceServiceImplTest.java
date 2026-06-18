package com.careermate.workspace;

import com.careermate.common.exception.BizException;
import com.careermate.model.entity.AgentMessageEntity;
import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.resume.version.dto.ResumeVersionListItemVO;
import com.careermate.resume.version.service.ResumeVersionService;
import com.careermate.workspace.dto.ActionAckResponse;
import com.careermate.workspace.dto.MessageVO;
import com.careermate.workspace.dto.WorkspaceCreateRequest;
import com.careermate.workspace.dto.WorkspaceCreateResponse;
import com.careermate.workspace.dto.WorkspaceVO;
import com.careermate.workspace.pending.ConfirmedPendingAction;
import com.careermate.workspace.pending.PendingActionCreateResult;
import com.careermate.workspace.pending.PendingActionService;
import com.careermate.workspace.service.impl.WorkspaceServiceImpl;
import com.careermate.workspace.support.WorkspaceSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceImplTest {

    @Mock
    private WorkspaceSessionRepository workspaceSessionRepository;
    @Mock
    private ResumeVersionService resumeVersionService;
    @Mock
    private PendingActionService pendingActionService;

    private WorkspaceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WorkspaceServiceImpl(
                workspaceSessionRepository,
                resumeVersionService,
                pendingActionService,
                new ObjectMapper()
        );
    }

    @Test
    void getWorkspaceReturnsJdSnapshot() {
        AgentSessionEntity session = baseSession(10L, 1L);
        session.setJdSnapshot("{\"company\":\"腾讯\",\"title\":\"算法工程师\"}");
        when(workspaceSessionRepository.requireSession(1L, "WS-abc")).thenReturn(session);
        when(resumeVersionService.listBySession(1L, "WS-abc")).thenReturn(List.of());

        WorkspaceVO vo = service.getWorkspace(1L, "WS-abc");

        assertEquals("腾讯", vo.jdSnapshot().get("company"));
        assertEquals("算法工程师", vo.jdSnapshot().get("title"));
        assertEquals(WorkspaceSessionRepository.WORKSPACE_JD_PREP, vo.workspaceType());
        assertEquals("腾讯 算法工程师", vo.goalText());
        assertEquals(List.of("📋 腾讯 算法工程师"), vo.contextChips());
        assertEquals("📋 腾讯 算法工程师", vo.contextSummary());
    }

    @Test
    void getWorkspaceReturnsGeneralTypeWithContextChips() {
        AgentSessionEntity session = baseSession(10L, 1L);
        session.setWorkspaceType(WorkspaceSessionRepository.WORKSPACE_GENERAL);
        session.setTitle("通用空间");
        session.setGoalText("随便聊聊");
        session.setWorkspaceMetadata("{\"entry\":\"mine\"}");
        when(workspaceSessionRepository.requireSession(1L, "WS-general")).thenReturn(session);
        when(resumeVersionService.listBySession(1L, "WS-general")).thenReturn(List.of());

        WorkspaceVO vo = service.getWorkspace(1L, "WS-general");

        assertEquals(WorkspaceSessionRepository.WORKSPACE_GENERAL, vo.workspaceType());
        assertEquals("随便聊聊", vo.goalText());
        assertEquals("mine", vo.workspaceMetadata().get("entry"));
        assertEquals(List.of("普通对话"), vo.contextChips());
    }

    @Test
    void getWorkspaceNormalizesChatAliasToGeneral() {
        AgentSessionEntity session = baseSession(10L, 1L);
        session.setWorkspaceType(WorkspaceSessionRepository.WORKSPACE_CHAT);
        when(workspaceSessionRepository.requireSession(1L, "WS-chat")).thenReturn(session);
        when(resumeVersionService.listBySession(1L, "WS-chat")).thenReturn(List.of());

        WorkspaceVO vo = service.getWorkspace(1L, "WS-chat");

        assertEquals(WorkspaceSessionRepository.WORKSPACE_GENERAL, vo.workspaceType());
        assertEquals(List.of("普通对话"), vo.contextChips());
    }

    @Test
    void getWorkspaceIncludesResumeVersionChipForJdPrep() {
        AgentSessionEntity session = baseSession(10L, 1L);
        session.setJdSnapshot("{}");
        when(workspaceSessionRepository.requireSession(1L, "WS-abc")).thenReturn(session);
        when(resumeVersionService.listBySession(1L, "WS-abc")).thenReturn(List.of(
                new ResumeVersionListItemVO("v1", "针对【腾讯】Java · v1", "腾讯 Java", "腾讯", 1L, "Java", 1, OffsetDateTime.now())
        ));

        WorkspaceVO vo = service.getWorkspace(1L, "WS-abc");

        assertEquals(List.of("JD 已加载", "简历版本 1"), vo.contextChips());
        assertEquals("JD 已加载 · 简历版本 1", vo.contextSummary());
    }

    @Test
    void getWorkspaceDegradesInvalidMetadataToEmptyMap() {
        AgentSessionEntity session = baseSession(10L, 1L);
        session.setWorkspaceMetadata("{not-json");
        session.setJdSnapshot("{bad-json");
        when(workspaceSessionRepository.requireSession(1L, "WS-bad")).thenReturn(session);
        when(resumeVersionService.listBySession(1L, "WS-bad")).thenReturn(List.of());

        WorkspaceVO vo = service.getWorkspace(1L, "WS-bad");

        assertTrue(vo.workspaceMetadata().isEmpty());
        assertTrue(vo.jdSnapshot().isEmpty());
    }

    @Test
    void getMessagesOrderedBySequenceNo() {
        AgentSessionEntity session = baseSession(10L, 1L);
        when(workspaceSessionRepository.requireSession(1L, "WS-abc")).thenReturn(session);
        AgentMessageEntity m1 = message(1L, 1, "assistant", "hello");
        AgentMessageEntity m2 = message(2L, 2, "user", "hi");
        when(workspaceSessionRepository.listMessages(10L, 1L, null, 50)).thenReturn(List.of(m1, m2));

        List<MessageVO> messages = service.getMessages(1L, "WS-abc", null, 50);

        assertEquals(2, messages.size());
        assertEquals(1, messages.get(0).sequenceNo());
        assertEquals(2, messages.get(1).sequenceNo());
    }

    @Test
    void getMessagesAfterParameterApplied() {
        AgentSessionEntity session = baseSession(10L, 1L);
        when(workspaceSessionRepository.requireSession(1L, "WS-abc")).thenReturn(session);
        when(workspaceSessionRepository.listMessages(10L, 1L, 3, 50)).thenReturn(List.of());

        service.getMessages(1L, "WS-abc", 3, 50);

        verify(workspaceSessionRepository).listMessages(10L, 1L, 3, 50);
    }

    @Test
    void actionGenerateResumeReturnsConfirmCardNotSse() {
        AgentSessionEntity session = baseSession(10L, 1L);
        when(workspaceSessionRepository.requireSession(1L, "WS-abc")).thenReturn(session);
        Map<String, Object> card = Map.of("type", "CONFIRM_ACTION", "actionId", "PA-1");
        when(pendingActionService.createGenerateResumePendingAction(1L, "WS-abc", "doc-1"))
                .thenReturn(new PendingActionCreateResult("PA-1", card, OffsetDateTime.now().plusMinutes(15)));

        ActionAckResponse ack = service.handleAction(1L, "WS-abc", "GENERATE_RESUME", "doc-1");

        assertTrue(ack.noop());
        assertEquals(null, ack.sseEndpoint());
        assertEquals("CONFIRM_ACTION", ack.card().get("type"));
        verify(workspaceSessionRepository).appendMessage(eq(1L), eq(session), eq("assistant"), anyString(),
                eq("CARD"), anyString(), eq(null));
    }

    @Test
    void actionConfirmPendingActionReturnsSseEndpointWithPendingActionId() {
        AgentSessionEntity session = baseSession(10L, 1L);
        when(workspaceSessionRepository.requireSession(1L, "WS-abc")).thenReturn(session);
        when(pendingActionService.confirm(1L, "WS-abc", "PA-1"))
                .thenReturn(new ConfirmedPendingAction("PA-1", "doc-1"));

        ActionAckResponse ack = service.handleAction(1L, "WS-abc", "CONFIRM_PENDING_ACTION", "{\"actionId\":\"PA-1\"}");

        assertFalse(ack.noop());
        assertNotNull(ack.sseEndpoint());
        assertTrue(ack.sseEndpoint().contains("pendingActionId=PA-1"));
        assertTrue(ack.sseEndpoint().contains("jdId=doc-1"));
    }

    @Test
    void actionCancelPendingActionReturnsCancelledCard() {
        AgentSessionEntity session = baseSession(10L, 1L);
        when(workspaceSessionRepository.requireSession(1L, "WS-abc")).thenReturn(session);
        when(pendingActionService.buildCancelledCard("PA-1"))
                .thenReturn(Map.of("type", "ACTION_CANCELLED", "actionId", "PA-1"));

        ActionAckResponse ack = service.handleAction(1L, "WS-abc", "CANCEL_PENDING_ACTION", "{\"actionId\":\"PA-1\"}");

        assertTrue(ack.noop());
        assertEquals("ACTION_CANCELLED", ack.card().get("type"));
        verify(pendingActionService).cancel(1L, "WS-abc", "PA-1");
    }

    @Test
    void actionRetryWithJsonPayloadReturnsConfirmCard() {
        AgentSessionEntity session = baseSession(10L, 1L);
        when(workspaceSessionRepository.requireSession(1L, "WS-abc")).thenReturn(session);
        Map<String, Object> card = Map.of("type", "CONFIRM_ACTION", "actionId", "PA-2");
        when(pendingActionService.createGenerateResumePendingAction(1L, "WS-abc", "doc-1"))
                .thenReturn(new PendingActionCreateResult("PA-2", card, OffsetDateTime.now().plusMinutes(15)));
        String jsonPayload = """
                {"sessionId":"WS-abc","jdId":"doc-1","failedStep":"LOAD_JD","retryable":true}
                """;

        ActionAckResponse ack = service.handleAction(1L, "WS-abc", "RETRY", jsonPayload);

        assertTrue(ack.noop());
        assertEquals("CONFIRM_ACTION", ack.card().get("type"));
    }

    @Test
    void actionRetryWithJsonPayloadNotRetryableReturnsNoop() {
        AgentSessionEntity session = baseSession(10L, 1L);
        when(workspaceSessionRepository.requireSession(1L, "WS-abc")).thenReturn(session);
        String jsonPayload = """
                {"sessionId":"WS-abc","jdId":"doc-1","failedStep":"LOAD_RESUME","retryable":false}
                """;

        ActionAckResponse ack = service.handleAction(1L, "WS-abc", "RETRY", jsonPayload);

        assertTrue(ack.noop());
        assertEquals(null, ack.sseEndpoint());
    }

    @Test
    void actionGenerateResumeUsesSessionJdWhenPayloadBlank() {
        AgentSessionEntity session = baseSession(10L, 1L);
        when(workspaceSessionRepository.requireSession(1L, "WS-abc")).thenReturn(session);
        when(pendingActionService.createGenerateResumePendingAction(1L, "WS-abc", "doc-1"))
                .thenReturn(new PendingActionCreateResult("PA-3",
                        Map.of("type", "CONFIRM_ACTION"), OffsetDateTime.now().plusMinutes(15)));

        ActionAckResponse ack = service.handleAction(1L, "WS-abc", "GENERATE_RESUME", null);

        assertTrue(ack.noop());
        assertEquals("CONFIRM_ACTION", ack.card().get("type"));
    }

    @Test
    void actionRetryWithInvalidJsonTreatedAsLegacyJdId() {
        AgentSessionEntity session = baseSession(10L, 1L);
        when(workspaceSessionRepository.requireSession(1L, "WS-abc")).thenReturn(session);
        when(pendingActionService.createGenerateResumePendingAction(1L, "WS-abc", "{not-json"))
                .thenReturn(new PendingActionCreateResult("PA-4",
                        Map.of("type", "CONFIRM_ACTION"), OffsetDateTime.now().plusMinutes(15)));

        ActionAckResponse ack = service.handleAction(1L, "WS-abc", "RETRY", "{not-json");

        assertTrue(ack.noop());
        assertEquals("CONFIRM_ACTION", ack.card().get("type"));
    }

    @Test
    void createWorkspaceCreatesInterviewWorkspaceWithMetadata() {
        AgentSessionEntity session = baseSession(11L, 1L);
        session.setSessionId("WS-interview");
        session.setWorkspaceType(WorkspaceSessionRepository.WORKSPACE_INTERVIEW);
        when(workspaceSessionRepository.createWorkspace(
                eq(1L),
                eq(WorkspaceSessionRepository.WORKSPACE_INTERVIEW),
                eq("Redis 面试题"),
                eq("讲解面试题"),
                eq(Map.of("questionText", "Redis 持久化"))
        )).thenReturn(session);
        when(workspaceSessionRepository.appendMessage(
                eq(1L), eq(session), eq("assistant"), anyString(), eq("CARD"), anyString(), eq(1)
        )).thenReturn(message(1L, 1, "assistant", "welcome"));

        WorkspaceCreateResponse response = service.createWorkspace(1L, new WorkspaceCreateRequest(
                WorkspaceSessionRepository.WORKSPACE_INTERVIEW,
                "Redis 面试题",
                "讲解面试题",
                "EXPLAIN_QUESTION",
                Map.of("questionText", "Redis 持久化")
        ));

        assertEquals("WS-interview", response.workspaceId());
        assertEquals("/chat/WS-interview", response.redirectPath());
        assertEquals(WorkspaceSessionRepository.WORKSPACE_INTERVIEW, response.workspaceType());
        verify(workspaceSessionRepository).appendMessage(
                eq(1L), eq(session), eq("assistant"), anyString(), eq("CARD"), anyString(), eq(1)
        );
    }

    @Test
    void createWorkspaceCreatesMarketWorkspaceWithWelcomeCard() {
        AgentSessionEntity session = baseSession(12L, 1L);
        session.setSessionId("WS-market");
        session.setWorkspaceType(WorkspaceSessionRepository.WORKSPACE_MARKET);
        when(workspaceSessionRepository.createWorkspace(
                anyLong(), anyString(), anyString(), anyString(), any()
        )).thenReturn(session);
        when(workspaceSessionRepository.appendMessage(
                anyLong(), eq(session), eq("assistant"), anyString(), eq("CARD"), anyString(), eq(1)
        )).thenReturn(message(1L, 1, "assistant", "welcome"));

        WorkspaceCreateResponse response = service.createWorkspace(1L, new WorkspaceCreateRequest(
                WorkspaceSessionRepository.WORKSPACE_MARKET,
                "广州 Java后端",
                "生成谈薪脚本",
                "NEGOTIATION_SCRIPT",
                Map.of("city", "广州", "role", "Java后端", "years", "3-5年")
        ));

        assertEquals("WS-market", response.workspaceId());
        assertEquals("/chat/WS-market", response.redirectPath());
        verify(workspaceSessionRepository).appendMessage(
                anyLong(), eq(session), eq("assistant"), anyString(), eq("CARD"), anyString(), eq(1)
        );
    }

    @Test
    void createWorkspaceRejectsUnknownWorkspaceType() {
        BizException ex = assertThrows(
                BizException.class,
                () -> service.createWorkspace(1L, new WorkspaceCreateRequest(
                        "PIPELINE", "title", "goal", "ACTION", Map.of()
                ))
        );
        assertEquals(400, ex.getCode());
    }

    @Test
    void createWorkspaceReturnsRedirectPath() {
        AgentSessionEntity session = baseSession(13L, 1L);
        session.setSessionId("WS-redirect");
        session.setWorkspaceType(WorkspaceSessionRepository.WORKSPACE_RESUME);
        when(workspaceSessionRepository.createWorkspace(
                anyLong(), anyString(), anyString(), anyString(), any()
        )).thenReturn(session);
        when(workspaceSessionRepository.appendMessage(
                anyLong(), eq(session), anyString(), anyString(), anyString(), anyString(), anyInt()
        )).thenReturn(message(1L, 1, "assistant", "welcome"));

        WorkspaceCreateResponse response = service.createWorkspace(1L, new WorkspaceCreateRequest(
                WorkspaceSessionRepository.WORKSPACE_RESUME,
                "简历优化",
                "继续优化",
                "CONTINUE_WITH_ASSET",
                null
        ));

        assertEquals("/chat/WS-redirect", response.redirectPath());
    }

    @Test
    void createWorkspaceRejectsNullUserId() {
        BizException ex = assertThrows(
                BizException.class,
                () -> service.createWorkspace(null, new WorkspaceCreateRequest(
                        WorkspaceSessionRepository.WORKSPACE_MARKET,
                        "title",
                        "goal",
                        "NEGOTIATION_SCRIPT",
                        Map.of()
                ))
        );
        assertEquals(401, ex.getCode());
    }

    @Test
    void tenantIsolationForbiddenForOtherUser() {
        when(workspaceSessionRepository.requireSession(2L, "WS-abc"))
                .thenThrow(new BizException(403, "无权访问该工作空间"));

        assertThrows(BizException.class, () -> service.getWorkspace(2L, "WS-abc"));
    }

    private static AgentSessionEntity baseSession(Long id, Long userId) {
        AgentSessionEntity session = new AgentSessionEntity();
        session.setId(id);
        session.setUserId(userId);
        session.setSessionId("WS-abc");
        session.setWorkspaceType("JD_PREP");
        session.setTitle("腾讯 算法工程师");
        session.setJdId("doc-1");
        session.setCreatedAt(OffsetDateTime.now());
        session.setUpdatedAt(OffsetDateTime.now());
        return session;
    }

    private static AgentMessageEntity message(Long id, int seq, String role, String content) {
        AgentMessageEntity entity = new AgentMessageEntity();
        entity.setId(id);
        entity.setSequenceNo(seq);
        entity.setRole(role);
        entity.setContent(content);
        entity.setMessageType("TEXT");
        entity.setCreatedAt(OffsetDateTime.now());
        return entity;
    }
}
