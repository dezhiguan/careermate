package com.careermate.workspace.controller;

import com.careermate.agent.sse.SseEmitterService;
import com.careermate.common.api.ApiResponse;
import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.resume.version.workflow.GenerateResumeFromJdWorkflow;
import com.careermate.security.CurrentUserContext;
import com.careermate.workspace.dto.ActionAckResponse;
import com.careermate.workspace.dto.ActionRequest;
import com.careermate.workspace.dto.MessageVO;
import com.careermate.workspace.dto.WorkspaceCreateRequest;
import com.careermate.workspace.dto.WorkspaceCreateResponse;
import com.careermate.workspace.dto.WorkspaceVO;
import com.careermate.workspace.pending.PendingActionService;
import com.careermate.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.FutureTask;

@Slf4j
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final GenerateResumeFromJdWorkflow generateResumeFromJdWorkflow;
    private final SseEmitterService sseEmitterService;
    private final TaskExecutor agentExecutor;
    private final PendingActionService pendingActionService;

    public WorkspaceController(
            WorkspaceService workspaceService,
            GenerateResumeFromJdWorkflow generateResumeFromJdWorkflow,
            SseEmitterService sseEmitterService,
            TaskExecutor agentExecutor,
            PendingActionService pendingActionService
    ) {
        this.workspaceService = workspaceService;
        this.generateResumeFromJdWorkflow = generateResumeFromJdWorkflow;
        this.sseEmitterService = sseEmitterService;
        this.agentExecutor = agentExecutor;
        this.pendingActionService = pendingActionService;
    }

    @PostMapping
    public ApiResponse<WorkspaceCreateResponse> createWorkspace(
            @Valid @RequestBody WorkspaceCreateRequest request
    ) {
        Long userId = CurrentUserContext.getUserId();
        return ApiResponse.success(workspaceService.createWorkspace(userId, request));
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<WorkspaceVO> getWorkspace(@PathVariable String sessionId) {
        Long userId = CurrentUserContext.getUserId();
        return ApiResponse.success(workspaceService.getWorkspace(userId, sessionId));
    }

    @GetMapping("/{sessionId}/messages")
    public ApiResponse<List<MessageVO>> getMessages(
            @PathVariable String sessionId,
            @RequestParam(required = false) Integer after,
            @RequestParam(defaultValue = "50") int limit
    ) {
        Long userId = CurrentUserContext.getUserId();
        return ApiResponse.success(workspaceService.getMessages(userId, sessionId, after, limit));
    }

    @PostMapping("/{sessionId}/action")
    public ApiResponse<ActionAckResponse> postAction(
            @PathVariable String sessionId,
            @Valid @RequestBody ActionRequest request
    ) {
        Long userId = CurrentUserContext.getUserId();
        return ApiResponse.success(workspaceService.handleAction(
                userId, sessionId, request.getAction(), request.getPayload()
        ));
    }

    @GetMapping("/{sessionId}/generate-resume/stream")
    public SseEmitter generateResumeStream(
            @PathVariable String sessionId,
            @RequestParam(required = false) String jdId,
            @RequestParam(required = false) String pendingActionId
    ) {
        Long userId = CurrentUserContext.getUserId();
        AgentSessionEntity session = workspaceService.requireOwnedSession(userId, sessionId);
        String targetJdId = resolveJdId(jdId, session);
        if (pendingActionId != null && !pendingActionId.isBlank()) {
            pendingActionService.validateAndConsumeConfirmed(userId, sessionId, pendingActionId, targetJdId);
        }
        log.info("generate resume stream: sessionId={}, userId={}, jdId={}, pendingActionId={}",
                sessionId, userId, targetJdId, pendingActionId);

        SseEmitter emitter = sseEmitterService.createEmitter(sessionId);
        FutureTask<Void> task = new FutureTask<>(() -> {
            generateResumeFromJdWorkflow.generate(userId, sessionId, targetJdId, sseEmitterService);
            return null;
        });
        agentExecutor.execute(task);
        return emitter;
    }

    private static String resolveJdId(String requestJdId, AgentSessionEntity session) {
        if (requestJdId != null && !requestJdId.isBlank()) {
            return requestJdId.trim();
        }
        return session.getJdId();
    }
}
