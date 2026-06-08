package com.careermate.agent.controller;

import com.careermate.agent.AgentPromptAssembler;
import com.careermate.agent.multiagent.AgentSupervisor;
import com.careermate.agent.config.AgentProperties;
import com.careermate.agent.context.AgentConversationContextProvider;
import com.careermate.agent.context.CareerProfileContextProvider;
import com.careermate.agent.context.CareerProfileContextResult;
import com.careermate.agent.context.ConversationContextResult;
import com.careermate.profile.CareerProfileAutoUpdateService;
import com.careermate.profile.CareerProfileUpdateResult;
import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.AgentToolExecutionService;
import com.careermate.agent.tool.AgentLlmIntentRecognizer;
import com.careermate.agent.tool.AgentToolResult;
import com.careermate.agent.tool.AgentToolTraceSupport;
import com.careermate.agent.dto.AgentMessageRequest;
import com.careermate.agent.dto.AgentSessionCreateResponse;
import com.careermate.agent.dto.AgentSessionListItemResponse;
import com.careermate.agent.dto.AgentSessionResponse;
import com.careermate.agent.dto.AgentTraceResponse;
import com.careermate.agent.session.AgentSessionService;
import com.careermate.jobmatch.JobMatchContext;
import com.careermate.jobmatch.JobMatchContextProvider;
import com.careermate.resume.ResumeContext;
import com.careermate.resume.ResumeContextProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.careermate.agent.sse.AgentTaskRegistry;
import com.careermate.agent.sse.SseEmitterService;
import com.careermate.agent.sse.SseEventType;
import com.careermate.common.api.ApiResponse;
import com.careermate.common.exception.BizException;
import com.careermate.llm.LlmClient;
import com.careermate.llm.provider.LlmProviderDefaults;
import com.careermate.llm.StreamCallback;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.llm.LlmProperties;
import com.careermate.observability.AgentTracing;
import com.careermate.observability.MdcKeys;
import com.careermate.security.CurrentUserContext;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RestController
@RequestMapping("/api/agent")
public class AgentStreamController {

    private final LlmClient llmClient;
    private final TaskExecutor agentExecutor;
    private final SseEmitterService sseEmitterService;
    private final AgentTaskRegistry taskRegistry;
    private final AgentSessionService agentSessionService;
    private final ResumeContextProvider resumeContextProvider;
    private final JobMatchContextProvider jobMatchContextProvider;
    private final ObjectMapper objectMapper;
    private final AgentProperties agentProperties;
    private final AgentLlmIntentRecognizer intentRecognizer;
    private final AgentToolExecutionService agentToolExecutionService;
    private final AgentConversationContextProvider conversationContextProvider;
    private final CareerProfileContextProvider careerProfileContextProvider;
    private final CareerProfileAutoUpdateService careerProfileAutoUpdateService;
    private final AgentTracing agentTracing;
    private final LlmProperties llmProperties;
    private final AgentSupervisor agentSupervisor;
    private final com.careermate.agent.react.ReActEngine reactEngine;

    private static final String TRACE_RESUME_CONTEXT = "resume_context";
    private static final String TRACE_JOB_MATCH_CONTEXT = "job_match_context";
    private static final String TRACE_CONVERSATION_CONTEXT = "conversation_context";
    private static final String TRACE_CAREER_PROFILE_CONTEXT = "career_profile_context";
    private static final String TRACE_CAREER_PROFILE_UPDATE = "career_profile_update";

    public AgentStreamController(
            LlmClient llmClient,
            TaskExecutor agentExecutor,
            SseEmitterService sseEmitterService,
            AgentTaskRegistry taskRegistry,
            AgentSessionService agentSessionService,
            ResumeContextProvider resumeContextProvider,
            JobMatchContextProvider jobMatchContextProvider,
            ObjectMapper objectMapper,
            AgentProperties agentProperties,
            AgentLlmIntentRecognizer intentRecognizer,
            AgentToolExecutionService agentToolExecutionService,
            AgentConversationContextProvider conversationContextProvider,
            CareerProfileContextProvider careerProfileContextProvider,
            CareerProfileAutoUpdateService careerProfileAutoUpdateService,
            AgentTracing agentTracing,
            LlmProperties llmProperties,
            AgentSupervisor agentSupervisor,
            com.careermate.agent.react.ReActEngine reactEngine
    ) {
        this.llmClient = llmClient;
        this.agentExecutor = agentExecutor;
        this.sseEmitterService = sseEmitterService;
        this.taskRegistry = taskRegistry;
        this.agentSessionService = agentSessionService;
        this.resumeContextProvider = resumeContextProvider;
        this.jobMatchContextProvider = jobMatchContextProvider;
        this.objectMapper = objectMapper;
        this.agentProperties = agentProperties;
        this.intentRecognizer = intentRecognizer;
        this.agentToolExecutionService = agentToolExecutionService;
        this.conversationContextProvider = conversationContextProvider;
        this.careerProfileContextProvider = careerProfileContextProvider;
        this.careerProfileAutoUpdateService = careerProfileAutoUpdateService;
        this.agentTracing = agentTracing;
        this.llmProperties = llmProperties;
        this.agentSupervisor = agentSupervisor;
        this.reactEngine = reactEngine;
    }

    @PostMapping("/sessions")
    public ApiResponse<AgentSessionCreateResponse> createSession() {
        Long userId = CurrentUserContext.getUserId();
        return ApiResponse.success(agentSessionService.createSession(userId));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<AgentSessionListItemResponse>> listSessions() {
        Long userId = CurrentUserContext.getUserId();
        return ApiResponse.success(agentSessionService.listRecentSessions(userId, 20));
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<AgentSessionResponse> getSession(@PathVariable String sessionId) {
        Long userId = CurrentUserContext.getUserId();
        return ApiResponse.success(agentSessionService.getSession(userId, sessionId));
    }

    @GetMapping("/sessions/{sessionId}/trace")
    public ApiResponse<List<AgentTraceResponse>> getTrace(@PathVariable String sessionId) {
        Long userId = CurrentUserContext.getUserId();
        return ApiResponse.success(agentSessionService.getTrace(userId, sessionId));
    }

    @PostMapping("/sessions/{sessionId}/messages/stream")
    public SseEmitter stream(
            @PathVariable String sessionId,
            @Valid @RequestBody AgentMessageRequest request
    ) {
        Long userId = CurrentUserContext.getUserId();
        log.info("Agent stream request: sessionId={}, userId={}", sessionId, userId);

        agentSessionService.getSession(userId, sessionId);

        Map<String, String> parentMdc = MDC.getCopyOfContextMap();
        FutureTask<Void> task = new FutureTask<>(() -> {
            if (parentMdc != null) {
                MDC.setContextMap(new HashMap<>(parentMdc));
            }
            MDC.put(MdcKeys.SESSION_ID, sessionId);
            MDC.put(MdcKeys.USER_ID, String.valueOf(userId));
            try {
                agentTracing.runStream(userId, sessionId, () -> runStreamingTask(userId, sessionId, request));
            } finally {
                MDC.remove(MdcKeys.SESSION_ID);
                MDC.remove(MdcKeys.USER_ID);
            }
            return null;
        });
        taskRegistry.startOrThrow(sessionId, task);

        SseEmitter emitter = sseEmitterService.createEmitter(sessionId);

        try {
            agentExecutor.execute(task);
        } catch (RuntimeException e) {
            taskRegistry.complete(sessionId);
            sseEmitterService.completeWithError(sessionId, e);
            throw e;
        }

        return emitter;
    }

    private void runStreamingTask(Long userId, String sessionId, AgentMessageRequest request) {
        long start = System.currentTimeMillis();
        ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("agent-heartbeat-" + sessionId);
            t.setDaemon(true);
            return t;
        });

        ScheduledFuture<?> heartbeatFuture = heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                sseEmitterService.send(sessionId, SseEventType.HEARTBEAT, Map.of("sessionId", sessionId));
            } catch (Exception ignored) {
            }
        }, 15, 15, TimeUnit.SECONDS);

        StringBuilder full = new StringBuilder();
        AtomicBoolean terminalHandled = new AtomicBoolean(false);
        Thread workerThread = Thread.currentThread();
        ScheduledFuture<?> taskTimeoutFuture = heartbeatExecutor.schedule(() -> {
            if (terminalHandled.compareAndSet(false, true)) {
                workerThread.interrupt();
                handleStreamError(
                        userId,
                        sessionId,
                        new BizException(504, "Agent 流式任务超时"),
                        "AGENT_STREAM_TIMEOUT"
                );
            }
        }, agentProperties.getStreamTaskTimeoutMs(), TimeUnit.MILLISECONDS);

        try {
            long phaseStart = System.currentTimeMillis();
            agentSessionService.appendMessage(userId, sessionId, "user", request.getMessage(), "text");
            logPhase(sessionId, "persist_user_message", phaseStart);

            Map<String, Object> planData = Map.of(
                    "steps", List.of("接收用户输入", "调用 LLM", "生成回复"),
                    "totalSteps", 3
            );
            sseEmitterService.send(sessionId, SseEventType.PLAN, planData);
            agentSessionService.recordTrace(
                    userId,
                    sessionId,
                    "PLAN",
                    toJson(planData),
                    toJson(planData),
                    "SUCCESS",
                    null,
                    null
            );

            phaseStart = System.currentTimeMillis();
            CareerProfileUpdateResult profileUpdate = careerProfileAutoUpdateService.tryAutoUpdateTargetRole(
                    userId,
                    request.getMessage()
            );
            recordCareerProfileUpdateTrace(userId, sessionId, profileUpdate);
            logPhase(sessionId, "career_profile_auto_update", phaseStart);

            phaseStart = System.currentTimeMillis();
            CareerProfileContextResult careerProfileContext = agentTracing.call(
                    "agent.load_profile_context",
                    userId,
                    sessionId,
                    null,
                    null,
                    null,
                    () -> careerProfileContextProvider.load(userId)
            );
            recordCareerProfileContextTrace(userId, sessionId, careerProfileContext);
            logPhase(sessionId, "load_career_profile_context", phaseStart);

            phaseStart = System.currentTimeMillis();
            ResumeContext resumeContext = agentTracing.call(
                    "agent.load_resume_context",
                    userId,
                    sessionId,
                    null,
                    null,
                    null,
                    () -> resumeContextProvider.getResumeContext(userId)
            );
            recordResumeContextTrace(userId, sessionId, resumeContext);
            logPhase(sessionId, "load_resume_context", phaseStart);

            phaseStart = System.currentTimeMillis();
            JobMatchContext jobMatchContext = jobMatchContextProvider.getLatestJobMatchContext(userId);
            recordJobMatchContextTrace(userId, sessionId, jobMatchContext);
            logPhase(sessionId, "load_job_match_context", phaseStart);

            phaseStart = System.currentTimeMillis();
            ConversationContextResult conversationContext = agentTracing.call(
                    "agent.load_conversation_context",
                    userId,
                    sessionId,
                    null,
                    null,
                    null,
                    () -> loadConversationContextSafely(userId, sessionId, request.getMessage())
            );
            recordConversationContextTrace(userId, sessionId, conversationContext);
            logPhase(sessionId, "load_conversation_context", phaseStart);

            String systemPrompt = AgentPromptAssembler.buildBaseSystemPrompt();
            systemPrompt = AgentPromptAssembler.appendCareerProfileContext(systemPrompt, careerProfileContext);
            systemPrompt = AgentPromptAssembler.appendResumeContext(systemPrompt, resumeContext);
            systemPrompt = AgentPromptAssembler.appendJobMatchContext(systemPrompt, jobMatchContext);
            systemPrompt = AgentPromptAssembler.appendConversationContext(systemPrompt, conversationContext);

            phaseStart = System.currentTimeMillis();
            // Multi-Agent：Supervisor 派发给专家 Agent，专家结果注入 system prompt
            AgentToolContext toolCtx = AgentToolContext.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .userMessage(request.getMessage())
                    .build();
            List<com.careermate.agent.multiagent.SpecialistResult> specialistResults =
                    agentSupervisor.dispatch(toolCtx, request.getMessage());
            for (com.careermate.agent.multiagent.SpecialistResult sr : specialistResults) {
                if (sr.toolSummary() != null && !sr.toolSummary().isBlank()) {
                    systemPrompt = AgentPromptAssembler.appendSpecialistResult(systemPrompt, sr);
                }
            }
            // 回退：若 Supervisor 无专家结果，走原有单工具路由（向后兼容）
            if (specialistResults.isEmpty()) {
                AgentToolResult toolResult = executeRoutedToolIfAny(userId, sessionId, request.getMessage());
                if (toolResult != null) {
                    systemPrompt = AgentPromptAssembler.appendToolResult(systemPrompt, toolResult);
                }
            }
            logPhase(sessionId, "supervisor_dispatch", phaseStart);

            // ReAct 推理链：非流式 LLM 推理循环，结果注入 system prompt
            phaseStart = System.currentTimeMillis();
            AgentToolContext reactCtx = AgentToolContext.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .userMessage(request.getMessage())
                    .build();
            com.careermate.agent.react.ReActTrace reactTrace =
                    reactEngine.run(reactCtx, request.getMessage(), systemPrompt);
            if (reactTrace.hasSteps()) {
                systemPrompt = AgentPromptAssembler.appendReActTrace(systemPrompt, reactTrace);
                log.info("ReAct trace injected: rounds={} reachedFinalAnswer={}",
                        reactTrace.rounds(), reactTrace.reachedFinalAnswer());
            }
            logPhase(sessionId, "react_reasoning", phaseStart);

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(List.of(
                            ChatMessage.builder().role("system").content(systemPrompt).build(),
                            ChatMessage.builder().role("user").content(request.getMessage()).build()
                    ))
                    .build();

            final long llmStart = System.currentTimeMillis();
            final long[] firstTokenAt = { -1L };
            log.info(
                    "[agent-timing] sessionId={} phase=llm_stream_start provider={} model={} promptChars={}",
                    sessionId,
                    llmProperties.getProvider(),
                    llmProperties.getModel(),
                    systemPrompt.length() + request.getMessage().length()
            );
            agentTracing.call(
                    "agent.llm.stream_chat",
                    userId,
                    sessionId,
                    null,
                    llmProperties.getProvider(),
                    llmProperties.getModel(),
                    () -> {
                        llmClient.streamChat(chatRequest, new StreamCallback() {
                @Override
                public void onToken(String token) {
                    if (terminalHandled.get()) {
                        return;
                    }
                    if (Thread.currentThread().isInterrupted()) {
                        throw new RuntimeException("cancelled");
                    }
                    if (token == null || token.isEmpty()) {
                        return;
                    }
                    if (firstTokenAt[0] < 0) {
                        firstTokenAt[0] = System.currentTimeMillis();
                        log.info(
                                "[agent-timing] sessionId={} phase=llm_first_token ttftMs={}",
                                sessionId,
                                firstTokenAt[0] - llmStart
                        );
                    }
                    full.append(token);
                    sseEmitterService.send(sessionId, SseEventType.TOKEN, Map.of("content", token));
                }

                @Override
                public void onComplete(ChatResponse response) {
                    if (!terminalHandled.compareAndSet(false, true)) {
                        return;
                    }
                    try {
                        String content = full.toString();
                        sseEmitterService.send(sessionId, SseEventType.MESSAGE, Map.of("content", content));
                        agentSessionService.appendMessage(userId, sessionId, "agent", content, "text");
                        agentSessionService.recordTrace(
                                userId,
                                sessionId,
                                "MESSAGE",
                                "{}",
                                toJson(Map.of("contentLength", content.length())),
                                "SUCCESS",
                                null,
                                null
                        );

                        long totalLatencyMs = System.currentTimeMillis() - start;
                        log.info(
                                "[agent-timing] sessionId={} phase=stream_total totalCostMs={} replyChars={}",
                                sessionId,
                                totalLatencyMs,
                                content.length()
                        );
                        Map<String, Object> doneData = Map.of(
                                "sessionId", sessionId,
                                "totalLatencyMs", totalLatencyMs
                        );
                        sseEmitterService.send(sessionId, SseEventType.DONE, doneData);
                        agentSessionService.recordTrace(
                                userId,
                                sessionId,
                                "DONE",
                                "{}",
                                toJson(doneData),
                                "SUCCESS",
                                totalLatencyMs,
                                null
                        );
                        agentSessionService.markCompleted(userId, sessionId, totalLatencyMs);
                        sseEmitterService.complete(sessionId);
                    } catch (Throwable t) {
                        handleStreamError(userId, sessionId, t, "SSE_COMPLETE_ERROR");
                    }
                }

                @Override
                public void onError(Throwable error) {
                    if (terminalHandled.compareAndSet(false, true)) {
                        handleStreamError(userId, sessionId, error, "LLM_ERROR");
                    }
                }
                        });
                        return null;
                    }
            );
            log.info(
                    "[agent-timing] sessionId={} phase=llm_stream_total costMs={} ttftMs={} replyChars={}",
                    sessionId,
                    System.currentTimeMillis() - llmStart,
                    firstTokenAt[0] < 0 ? -1 : firstTokenAt[0] - llmStart,
                    full.length()
            );
            if (terminalHandled.compareAndSet(false, true)) {
                handleStreamError(
                        userId,
                        sessionId,
                        new IllegalStateException("LLM stream returned without terminal callback"),
                        "LLM_NO_TERMINAL_EVENT"
                );
            }
        } catch (Throwable t) {
            if (terminalHandled.compareAndSet(false, true)) {
                handleStreamError(userId, sessionId, t, "SSE_ERROR");
            }
        } finally {
            try {
                heartbeatFuture.cancel(true);
            } catch (Exception ignored) {
            }
            try {
                taskTimeoutFuture.cancel(true);
            } catch (Exception ignored) {
            }
            heartbeatExecutor.shutdownNow();
            taskRegistry.complete(sessionId);
        }
    }

    private void logPhase(String sessionId, String phase, long phaseStartMs) {
        log.info(
                "[agent-timing] sessionId={} phase={} costMs={}",
                sessionId,
                phase,
                System.currentTimeMillis() - phaseStartMs
        );
    }

    private void handleStreamError(Long userId, String sessionId, Throwable error, String errorCode) {
        String message = LlmProviderDefaults.safeStreamErrorMessage(error);
        sseEmitterService.send(sessionId, SseEventType.ERROR, Map.of("message", message));
        agentSessionService.recordTrace(
                userId,
                sessionId,
                "ERROR",
                "{}",
                toJson(Map.of("message", message)),
                "FAILED",
                null,
                errorCode
        );
        agentSessionService.markError(userId, sessionId, errorCode);
        sseEmitterService.complete(sessionId);
    }

    private AgentToolResult executeRoutedToolIfAny(Long userId, String sessionId, String userMessage) {
        return agentTracing.call(
                "agent.route_tool",
                userId,
                sessionId,
                null,
                null,
                null,
                () -> intentRecognizer.route(userMessage)
                .map(routed -> {
                    String toolName = routed.toolName();
                    Map<String, Object> startData = Map.of(
                            "toolName", toolName,
                            "summary", agentToolExecutionService.startSummary(toolName)
                    );
                    sseEmitterService.send(sessionId, SseEventType.TOOL_START, startData);

                    long toolStart = System.currentTimeMillis();
                    AgentToolContext context = AgentToolContext.builder()
                            .userId(userId)
                            .sessionId(sessionId)
                            .userMessage(userMessage)
                            .args(routed.args())
                            .build();
                    AgentToolResult result = agentTracing.call(
                            "agent.execute_tool",
                            userId,
                            sessionId,
                            toolName,
                            null,
                            null,
                            () -> agentToolExecutionService.execute(context, toolName)
                    );
                    long latencyMs = System.currentTimeMillis() - toolStart;

                    Map<String, Object> resultData = Map.of(
                            "toolName", toolName,
                            "success", result.isSuccess(),
                            "summary", result.getSummary()
                    );
                    sseEmitterService.send(sessionId, SseEventType.TOOL_RESULT, resultData);

                    String requestSummary = AgentToolTraceSupport.buildRequestSummary(
                            toolName,
                            routed.args(),
                            userMessage
                    );
                    String responseSummary = AgentToolTraceSupport.buildResponseSummary(result, objectMapper);
                    agentSessionService.recordTrace(
                            userId,
                            sessionId,
                            toolName,
                            requestSummary,
                            responseSummary,
                            result.isSuccess() ? "SUCCESS" : "FAILED",
                            latencyMs,
                            result.isSuccess() ? null : "TOOL_EXEC_FAILED"
                    );
                    return result;
                })
                .orElse(null)
        );
    }

    private void recordJobMatchContextTrace(Long userId, String sessionId, JobMatchContext jobMatchContext) {
        boolean available = jobMatchContext != null && jobMatchContext.isAvailable();
        String status = available ? "SUCCESS" : "EMPTY";
        String message = available
                ? "已加载最近岗位匹配：" + jobMatchContext.getJobTitle()
                : "当前用户暂无岗位匹配记录";

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        if (available) {
            payload.put("jobMatchId", jobMatchContext.getJobMatchId());
            payload.put("jobTitle", jobMatchContext.getJobTitle());
            payload.put("companyName", jobMatchContext.getCompanyName());
            payload.put("matchScore", jobMatchContext.getMatchScore());
            payload.put("matchLevel", jobMatchContext.getMatchLevel());
            payload.put("matchedSkillsCount", sizeOf(jobMatchContext.getMatchedSkills()));
            payload.put("missingSkillsCount", sizeOf(jobMatchContext.getMissingSkills()));
        }
        payload.put("message", message);

        agentSessionService.recordTrace(
                userId,
                sessionId,
                TRACE_JOB_MATCH_CONTEXT,
                "{}",
                writeJson(payload),
                status,
                null,
                null
        );
    }

    private int sizeOf(List<String> items) {
        return items == null ? 0 : items.size();
    }

    private void recordCareerProfileContextTrace(
            Long userId,
            String sessionId,
            CareerProfileContextResult result
    ) {
        String status = result != null && result.isAvailable() ? "SUCCESS" : "EMPTY";
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("available", result != null && result.isAvailable());
        if (result != null && result.isAvailable()) {
            payload.put("targetRole", result.getTargetRole());
            payload.put("skillCount", result.getSkillCount());
        }
        agentSessionService.recordTrace(
                userId,
                sessionId,
                TRACE_CAREER_PROFILE_CONTEXT,
                "{}",
                writeJson(payload),
                status,
                null,
                null
        );
    }

    private void recordCareerProfileUpdateTrace(
            Long userId,
            String sessionId,
            CareerProfileUpdateResult result
    ) {
        if (result == null || !result.isUpdated()) {
            return;
        }
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("updatedFields", result.getUpdatedFields());
        payload.put("targetRole", result.getTargetRole());
        agentSessionService.recordTrace(
                userId,
                sessionId,
                TRACE_CAREER_PROFILE_UPDATE,
                "{}",
                writeJson(payload),
                "SUCCESS",
                null,
                null
        );
    }

    private ConversationContextResult loadConversationContextSafely(
            Long userId,
            String sessionId,
            String currentUserMessage
    ) {
        try {
            ConversationContextResult result = conversationContextProvider.load(
                    userId,
                    sessionId,
                    currentUserMessage
            );
            if (result.isLoadFailed()) {
                recordConversationContextFailedTrace(userId, sessionId);
                return ConversationContextResult.empty();
            }
            return result;
        } catch (Exception e) {
            log.warn("conversation_context failed: userId={}, sessionId={}", userId, sessionId, e);
            recordConversationContextFailedTrace(userId, sessionId);
            return ConversationContextResult.empty();
        }
    }

    private void recordConversationContextTrace(Long userId, String sessionId, ConversationContextResult result) {
        if (result == null || result.isLoadFailed()) {
            return;
        }
        String status = result.isAvailable() ? "SUCCESS" : "EMPTY";
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("messageCount", result.getMessageCount());
        payload.put("charCount", result.getCharCount());
        agentSessionService.recordTrace(
                userId,
                sessionId,
                TRACE_CONVERSATION_CONTEXT,
                "{}",
                writeJson(payload),
                status,
                null,
                null
        );
    }

    private void recordConversationContextFailedTrace(Long userId, String sessionId) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("messageCount", 0);
        payload.put("charCount", 0);
        agentSessionService.recordTrace(
                userId,
                sessionId,
                TRACE_CONVERSATION_CONTEXT,
                "{}",
                writeJson(payload),
                "FAILED",
                null,
                "CONVERSATION_CONTEXT_FAILED"
        );
    }

    private void recordResumeContextTrace(Long userId, String sessionId, ResumeContext resumeContext) {
        boolean available = resumeContext != null && resumeContext.isAvailable();
        String status = available ? "SUCCESS" : "EMPTY";
        String message = available
                ? "已加载默认简历：" + resumeContext.getTitle()
                : "当前用户暂无默认简历";
        int contentLength = 0;
        if (available && resumeContext.getContent() != null) {
            contentLength = resumeContext.getContent().length();
        }
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        if (available) {
            payload.put("resumeId", resumeContext.getResumeId());
            payload.put("title", resumeContext.getTitle());
        }
        payload.put("contentLength", contentLength);
        payload.put("message", message);

        agentSessionService.recordTrace(
                userId,
                sessionId,
                TRACE_RESUME_CONTEXT,
                "{}",
                writeJson(payload),
                status,
                null,
                null
        );
    }

    private String writeJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return toJson(data);
        }
    }

    private String toJson(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else if (value instanceof List<?> list) {
                sb.append('[');
                boolean itemFirst = true;
                for (Object item : list) {
                    if (!itemFirst) {
                        sb.append(',');
                    }
                    itemFirst = false;
                    sb.append('"').append(String.valueOf(item)).append('"');
                }
                sb.append(']');
            } else {
                sb.append('"').append(String.valueOf(value)).append('"');
            }
        }
        sb.append('}');
        return sb.toString();
    }
}
