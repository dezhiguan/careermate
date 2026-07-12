package com.careermate.agent.tool;

import com.careermate.agent.sse.SseEmitterService;
import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.workspace.pending.PendingActionCreateResult;
import com.careermate.workspace.pending.PendingActionService;
import com.careermate.workspace.support.WorkspaceSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 5.13 回归：对话工具路径不再直接落库生成，而是产出 HITL 确认卡（pendingAction），
 * 确认后才由已确认的 stream 真正生成。
 */
@ExtendWith(MockitoExtension.class)
class GenerateResumeFromJdToolConfirmTest {

    @Mock
    private WorkspaceSessionRepository sessionRepository;
    @Mock
    private SseEmitterService sseEmitterService;
    @Mock
    private PendingActionService pendingActionService;

    @Test
    void executeCreatesConfirmationInsteadOfGenerating() {
        GenerateResumeFromJdTool tool =
                new GenerateResumeFromJdTool(sessionRepository, sseEmitterService, pendingActionService);

        AgentSessionEntity session = new AgentSessionEntity();
        session.setJdId("doc-1");
        when(sessionRepository.requireSession(1L, "WS-x")).thenReturn(session);
        Map<String, Object> card = Map.of("type", "CONFIRM_ACTION");
        when(pendingActionService.createGenerateResumePendingAction(1L, "WS-x", "doc-1"))
                .thenReturn(new PendingActionCreateResult("PA-1", card, OffsetDateTime.now().plusMinutes(15)));

        AgentToolContext ctx = AgentToolContext.builder().userId(1L).sessionId("WS-x").build();
        AgentToolResult result = tool.execute(ctx);

        assertTrue(result.isSuccess());
        assertEquals(Boolean.TRUE, result.getData().get("requiresConfirmation"));
        assertEquals("PA-1", result.getData().get("actionId"));
        verify(pendingActionService).createGenerateResumePendingAction(1L, "WS-x", "doc-1");
    }
}
