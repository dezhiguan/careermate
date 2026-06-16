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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class GenerateResumeFromJdWorkflow {

    private static final Pattern META_BLOCK_PATTERN = Pattern.compile(
            "```meta\\s*([\\s\\S]*?)```",
            Pattern.CASE_INSENSITIVE
    );

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
                failure.retryable()
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
                    failure.retryable()
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
        if (raw == null) {
            return new MetaParseResult("", List.of());
        }
        Matcher matcher = META_BLOCK_PATTERN.matcher(raw);
        String markdown = raw;
        List<Map<String, Object>> changes = List.of();
        if (matcher.find()) {
            markdown = matcher.replaceFirst("").trim();
            String json = matcher.group(1).trim();
            changes = parseChangesJson(json);
        }
        markdown = MarkdownExportSupport.stripWrappingFence(markdown);
        return new MetaParseResult(markdown, changes);
    }

    private static List<Map<String, Object>> parseChangesJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> root = mapper.readValue(json, new TypeReference<>() {});
            Object changes = root.get("changes");
            if (changes instanceof List<?> list) {
                List<Map<String, Object>> result = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        Map<String, Object> cast = new LinkedHashMap<>();
                        map.forEach((k, v) -> cast.put(String.valueOf(k), v));
                        result.add(cast);
                    }
                }
                return result;
            }
            return List.of();
        } catch (Exception e) {
            return List.of();
        }
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
