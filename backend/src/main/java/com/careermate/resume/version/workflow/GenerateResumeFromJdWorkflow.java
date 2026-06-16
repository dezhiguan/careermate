package com.careermate.resume.version.workflow;

import com.careermate.agent.session.AgentSessionService;
import com.careermate.agent.sse.SseEmitterService;
import com.careermate.agent.sse.SseEventType;
import com.careermate.common.exception.BizException;
import com.careermate.llm.LlmClient;
import com.careermate.ragforge.RagForgeClient;
import com.careermate.resume.ResumeContextProvider;
import com.careermate.resume.version.export.MarkdownExportSupport;
import com.careermate.resume.version.service.ResumeVersionService;
import com.careermate.workspace.support.WorkspaceSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GenerateResumeFromJdWorkflow {

    private final GenerateResumeWorkflowRunner runner;
    private final AgentSessionService agentSessionService;
    private final WorkspaceSessionRepository workspaceSessionRepository;
    private final ObjectMapper objectMapper;

    public GenerateResumeFromJdWorkflow(
            WorkspaceSessionRepository workspaceSessionRepository,
            ResumeContextProvider resumeContextProvider,
            RagForgeClient ragForgeClient,
            LlmClient llmClient,
            ResumeVersionService resumeVersionService,
            AgentSessionService agentSessionService,
            ObjectMapper objectMapper
    ) {
        this.workspaceSessionRepository = workspaceSessionRepository;
        this.agentSessionService = agentSessionService;
        this.objectMapper = objectMapper;
        this.runner = new GenerateResumeWorkflowRunner(
                workspaceSessionRepository,
                resumeContextProvider,
                ragForgeClient,
                llmClient,
                resumeVersionService,
                objectMapper
        );
    }

    public void generate(Long userId, String sessionId, String jdId, SseEmitterService sseEmitterService) {
        try {
            doGenerate(userId, sessionId, jdId, sseEmitterService);
        } catch (BizException e) {
            log.warn("generate resume failed: sessionId={}, code={}, msg={}", sessionId, e.getCode(), e.getMessage());
            GenerateResumeWorkflowStep failedStep = runner.inferFailedStep(e);
            GenerateResumeWorkflowFailure failure = runner.toFailure(failedStep, e);
            pushError(sseEmitterService, sessionId, jdId, failure);
            appendFailedCard(userId, sessionId, jdId, failure);
            if (sseEmitterService != null) {
                sseEmitterService.complete(sessionId);
            }
        } catch (Exception e) {
            log.error("generate resume unexpected error: sessionId={}", sessionId, e);
            GenerateResumeWorkflowFailure failure = runner.toFailure(
                    GenerateResumeWorkflowStep.GENERATE_RESUME, e
            );
            pushError(sseEmitterService, sessionId, jdId, failure);
            appendFailedCard(userId, sessionId, jdId, failure);
            if (sseEmitterService != null) {
                sseEmitterService.complete(sessionId);
            }
        }
    }

    /**
     * 不通过 SSE 推送、仅生成并落库，返回供调用方广播的卡片 Map。
     * 由 GenerateResumeFromJdTool 调用（工具调用场景，SSE 生命周期由 Agent 流管理）。
     */
    public Map<String, Object> generateAndReturnCard(Long userId, String sessionId, String jdId) {
        return doGenerate(userId, sessionId, jdId, null);
    }

    protected Map<String, Object> doGenerate(Long userId, String sessionId, String jdId, SseEmitterService sseEmitterService) {
        GenerateResumeWorkflowRun run = new GenerateResumeWorkflowRun(userId, sessionId, jdId, sseEmitterService);
        GenerateResumeWorkflowEventSink eventSink = new GenerateResumeWorkflowEventSink(
                agentSessionService, userId, sessionId
        );
        GenerateResumeWorkflowResult result = runner.execute(run, eventSink);
        return result.card();
    }

    private void pushError(
            SseEmitterService sseEmitterService,
            String sessionId,
            String jdId,
            GenerateResumeWorkflowFailure failure
    ) {
        if (sseEmitterService == null) {
            return;
        }
        sseEmitterService.send(sessionId, SseEventType.ERROR, Map.of("message", failure.userMessage()));
        Map<String, Object> card = GenerateResumeWorkflowRunner.buildFailedCard(
                sessionId,
                jdId,
                failure.failedStep(),
                failure.userMessage(),
                failure.retryable(),
                objectMapper
        );
        sseEmitterService.send(sessionId, SseEventType.UI_ACTION, Map.of("card", card));
    }

    private void appendFailedCard(
            Long userId,
            String sessionId,
            String jdId,
            GenerateResumeWorkflowFailure failure
    ) {
        try {
            var session = workspaceSessionRepository.requireSession(userId, sessionId);
            String effectiveJdId = jdId != null && !jdId.isBlank() ? jdId : session.getJdId();
            Map<String, Object> card = GenerateResumeWorkflowRunner.buildFailedCard(
                    sessionId,
                    effectiveJdId,
                    failure.failedStep(),
                    failure.userMessage(),
                    failure.retryable(),
                    objectMapper
            );
            workspaceSessionRepository.appendMessage(
                    userId,
                    session,
                    "assistant",
                    "简历生成失败：" + failure.userMessage(),
                    "CARD",
                    writeJson(Map.of("card", card)),
                    null
            );
        } catch (Exception e) {
            log.warn("append failed card error: {}", e.getMessage());
        }
    }

    static MetaParseResult parseMetaBlock(String raw) {
        MarkdownExportSupport.OptimizationMetaStripResult result =
                MarkdownExportSupport.stripOptimizationMeta(raw);
        return new MetaParseResult(result.markdown(), result.changes());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    record MetaParseResult(String markdown, List<Map<String, Object>> changes) {
    }
}
