package com.careermate.resume.version.workflow;

import com.careermate.agent.session.AgentSessionService;

/**
 * 记录 Workflow 各 step 的 trace，T12 HITL 可扩展为同时 emit 等待确认事件。
 */
public class GenerateResumeWorkflowEventSink {

    private final AgentSessionService agentSessionService;
    private final Long userId;
    private final String sessionId;

    public GenerateResumeWorkflowEventSink(AgentSessionService agentSessionService, Long userId, String sessionId) {
        this.agentSessionService = agentSessionService;
        this.userId = userId;
        this.sessionId = sessionId;
    }

    public void recordSuccess(GenerateResumeWorkflowStep step, String requestSummary, String responseSummary, long latencyMs) {
        agentSessionService.recordTrace(
                userId,
                sessionId,
                step.traceName(),
                requestSummary,
                responseSummary,
                "SUCCESS",
                latencyMs,
                null
        );
    }

    public void recordFailure(GenerateResumeWorkflowStep step, String requestSummary, String errorCode, long latencyMs) {
        agentSessionService.recordTrace(
                userId,
                sessionId,
                step.traceName(),
                requestSummary,
                "{}",
                "FAILED",
                latencyMs,
                errorCode
        );
    }

    /**
     * T12 HITL 扩展点：在 SAVE_VERSION 前暂停并等待人工确认时可调用。
     */
    public void onAwaitingHumanApproval(GenerateResumeWorkflowStep step, String summary) {
        // no-op for T08
    }
}
