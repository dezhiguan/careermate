package com.careermate.agent.service;

import com.careermate.agent.AgentPromptAssembler;
import com.careermate.agent.multiagent.AgentSupervisor;
import com.careermate.agent.config.AgentKernelProperties;
import com.careermate.agent.config.AgentProperties;
import com.careermate.agent.context.AgentConversationContextProvider;
import com.careermate.agent.context.CareerProfileContextProvider;
import com.careermate.agent.context.CareerProfileContextResult;
import com.careermate.agent.context.ConversationContextResult;
import com.careermate.profile.service.CareerProfileAutoUpdateService;
import com.careermate.profile.CareerProfileUpdateResult;
import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.AgentToolExecutionService;
import com.careermate.agent.tool.AgentLlmIntentRecognizer;
import com.careermate.agent.tool.AgentToolResult;
import com.careermate.agent.tool.AgentToolTraceSupport;
import com.careermate.agent.memory.AgentMemoryService;
import com.careermate.agent.dto.AgentMessageRequest;
import com.careermate.agent.runtime.AgentEvent;
import com.careermate.agent.runtime.AgentKernelEventTypes;
import com.careermate.agent.runtime.AgentKernelService;
import com.careermate.agent.runtime.AgentRunRequest;
import com.careermate.agent.runtime.AgentRunResult;
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
import com.careermate.common.exception.BizException;
import com.careermate.llm.LlmClient;
import com.careermate.llm.provider.LlmProviderDefaults;
import com.careermate.llm.StreamCallback;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.llm.LlmProperties;
import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.observability.AgentTracing;
import com.careermate.observability.MdcKeys;
import com.careermate.prompt.PromptRenderResult;
import com.careermate.prompt.PromptTemplateService;
import com.careermate.workspace.support.WorkspaceSessionRepository;
import org.slf4j.MDC;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
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
@Service
public class AgentStreamService {

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
    private final AgentMemoryService agentMemoryService;
    private final AgentTracing agentTracing;
    private final LlmProperties llmProperties;
    private final AgentSupervisor agentSupervisor;
    private final com.careermate.agent.react.ReActEngine reactEngine;
    private final WorkspaceSessionRepository workspaceSessionRepository;
    private final AgentKernelService agentKernelService;
    private final AgentKernelProperties agentKernelProperties;
    private final PromptTemplateService promptTemplateService;
    private final ChatClientStreamAdapter chatClientStreamAdapter;

    private static final String TRACE_RESUME_CONTEXT = "resume_context";
    private static final String TRACE_JOB_MATCH_CONTEXT = "job_match_context";
    private static final String TRACE_CONVERSATION_CONTEXT = "conversation_context";
    private static final String TRACE_CAREER_PROFILE_CONTEXT = "career_profile_context";
    private static final String TRACE_MEMORY_CONTEXT_LOADED = "memory_context_loaded";
    private static final String TRACE_CAREER_PROFILE_UPDATE = "career_profile_update";
    private static final String TRACE_PROMPT_TEMPLATE = "prompt_template";

    public AgentStreamService(
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
            AgentMemoryService agentMemoryService,
            AgentTracing agentTracing,
            LlmProperties llmProperties,
            AgentSupervisor agentSupervisor,
            com.careermate.agent.react.ReActEngine reactEngine,
            WorkspaceSessionRepository workspaceSessionRepository,
            AgentKernelService agentKernelService,
            AgentKernelProperties agentKernelProperties,
            PromptTemplateService promptTemplateService,
            ChatClientStreamAdapter chatClientStreamAdapter
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
        this.agentMemoryService = agentMemoryService;
        this.agentTracing = agentTracing;
        this.llmProperties = llmProperties;
        this.agentSupervisor = agentSupervisor;
        this.reactEngine = reactEngine;
        this.workspaceSessionRepository = workspaceSessionRepository;
        this.agentKernelService = agentKernelService;
        this.agentKernelProperties = agentKernelProperties;
        this.promptTemplateService = promptTemplateService;
        this.chatClientStreamAdapter = chatClientStreamAdapter;
    }

    public SseEmitter stream(Long userId, String sessionId, AgentMessageRequest request) {
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

            ChatRequest chatRequest;
            String systemPrompt;
            if (agentKernelProperties.isEnabled()) {
                phaseStart = System.currentTimeMillis();
                AgentRunResult runResult = agentKernelService.prepareRun(
                        AgentRunRequest.builder()
                                .userId(userId)
                                .sessionId(sessionId)
                                .userMessage(request.getMessage())
                                .build(),
                        event -> applyKernelEvent(userId, sessionId, event)
                );
                chatRequest = runResult.getChatRequest();
                systemPrompt = runResult.getSystemPrompt();
                logPhase(sessionId, "kernel_prepare_run", phaseStart);
            } else {
                chatRequest = prepareLegacyRun(userId, sessionId, request.getMessage());
                systemPrompt = chatRequest.getMessages().get(0).getContent();
            }

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
                        StreamCallback streamCallback = new StreamCallback() {
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
                        refreshConversationSummarySafely(userId, sessionId);
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
                        };
                        // A1-1: 框架开关开启走 Spring AI ChatClient 流式，否则回退自研 LlmClient
                        if (chatClientStreamAdapter.isEnabled()) {
                            chatClientStreamAdapter.stream(systemPrompt, request.getMessage(), streamCallback);
                        } else {
                            llmClient.streamChat(chatRequest, streamCallback);
                        }
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

    private ChatRequest prepareLegacyRun(Long userId, String sessionId, String userMessage) {
        long phaseStart = System.currentTimeMillis();
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
                userMessage
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
                () -> careerProfileContextProvider.load(userId, sessionId)
        );
        recordCareerProfileContextTrace(userId, sessionId, careerProfileContext);
        recordMemoryContextTrace(userId, sessionId, careerProfileContext);
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
                () -> loadConversationContextSafely(userId, sessionId, userMessage)
        );
        recordConversationContextTrace(userId, sessionId, conversationContext);
        logPhase(sessionId, "load_conversation_context", phaseStart);

        PromptRenderResult basePrompt = promptTemplateService.render("agent-base");
        recordPromptTemplateTrace(userId, sessionId, basePrompt.promptId(), basePrompt.version());
        String systemPrompt = AgentPromptAssembler.buildBaseSystemPrompt(basePrompt.content());
        systemPrompt = AgentPromptAssembler.appendCareerProfileContext(systemPrompt, careerProfileContext);
        systemPrompt = AgentPromptAssembler.appendResumeContext(systemPrompt, resumeContext);
        systemPrompt = AgentPromptAssembler.appendJobMatchContext(systemPrompt, jobMatchContext);
        systemPrompt = AgentPromptAssembler.appendConversationContext(systemPrompt, conversationContext);

        try {
            AgentSessionEntity wsSession = workspaceSessionRepository.getSessionIfExists(userId, sessionId);
            if (wsSession != null) {
                systemPrompt = AgentPromptAssembler.appendWorkspaceContext(
                        systemPrompt,
                        wsSession.getWorkspaceType(),
                        wsSession.getJdId(),
                        wsSession.getJdSnapshot()
                );
            }
        } catch (Exception ignored) {
            // 工作空间上下文加载失败不中断主流程
        }

        phaseStart = System.currentTimeMillis();
        AgentToolContext toolCtx = AgentToolContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .userMessage(userMessage)
                .build();
        List<com.careermate.agent.multiagent.SpecialistResult> specialistResults =
                agentSupervisor.dispatch(toolCtx, userMessage);
        boolean specialistBlocked = specialistResults.stream()
                .anyMatch(result -> result.getStatus() == com.careermate.agent.multiagent.SpecialistResultStatus.BLOCKED);
        for (com.careermate.agent.multiagent.SpecialistResult sr : specialistResults) {
            if (AgentPromptAssembler.shouldAppendSpecialistResult(sr)) {
                systemPrompt = AgentPromptAssembler.appendSpecialistResult(systemPrompt, sr);
            }
        }
        if (AgentPromptAssembler.shouldRunLegacyToolFallback(specialistBlocked, specialistResults)) {
            AgentToolResult toolResult = executeRoutedToolIfAny(userId, sessionId, userMessage);
            if (toolResult != null) {
                systemPrompt = AgentPromptAssembler.appendToolResult(systemPrompt, toolResult);
            }
        }
        logPhase(sessionId, "supervisor_dispatch", phaseStart);

        phaseStart = System.currentTimeMillis();
        if (AgentPromptAssembler.shouldRunReAct(specialistBlocked)) {
            AgentToolContext reactCtx = AgentToolContext.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .userMessage(userMessage)
                    .build();
            com.careermate.agent.react.ReActTrace reactTrace =
                    reactEngine.run(reactCtx, userMessage, systemPrompt);
            if (reactTrace.hasSteps()) {
                systemPrompt = AgentPromptAssembler.appendReActTrace(systemPrompt, reactTrace);
                log.info("ReAct trace injected: rounds={} reachedFinalAnswer={}",
                        reactTrace.rounds(), reactTrace.reachedFinalAnswer());
            }
        }
        logPhase(sessionId, "react_reasoning", phaseStart);

        return ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.builder().role("system").content(systemPrompt).build(),
                        ChatMessage.builder().role("user").content(userMessage).build()
                ))
                .build();
    }

    private void applyKernelEvent(Long userId, String sessionId, AgentEvent event) {
        if (AgentKernelEventTypes.PLAN.equals(event.getType())) {
            sseEmitterService.send(sessionId, SseEventType.PLAN, event.getPayload());
            agentSessionService.recordTrace(
                    userId,
                    sessionId,
                    "PLAN",
                    toJson(event.getPayload()),
                    toJson(event.getPayload()),
                    "SUCCESS",
                    null,
                    null
            );
            return;
        }
        if (AgentKernelEventTypes.TOOL_START.equals(event.getType())) {
            sseEmitterService.send(sessionId, SseEventType.TOOL_START, event.getPayload());
            Map<String, Object> payload = event.getPayload();
            agentSessionService.recordTrace(
                    userId,
                    sessionId,
                    String.valueOf(payload.get("toolName")),
                    "{}",
                    "{}",
                    "RUNNING",
                    null,
                    null
            );
            return;
        }
        if (AgentKernelEventTypes.TOOL_RESULT.equals(event.getType())) {
            sseEmitterService.send(sessionId, SseEventType.TOOL_RESULT, event.getPayload());
            return;
        }
        if (AgentKernelEventTypes.TRACE.equals(event.getType())) {
            Map<String, Object> payload = event.getPayload();
            Long latencyMs = payload.get("latencyMs") == null
                    ? null
                    : ((Number) payload.get("latencyMs")).longValue();
            agentSessionService.recordTrace(
                    userId,
                    sessionId,
                    String.valueOf(payload.get("traceName")),
                    String.valueOf(payload.get("requestSummary")),
                    String.valueOf(payload.get("responseSummary")),
                    String.valueOf(payload.get("status")),
                    latencyMs,
                    payload.get("errorCode") == null ? null : String.valueOf(payload.get("errorCode"))
            );
        }
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

    private void recordMemoryContextTrace(
            Long userId,
            String sessionId,
            CareerProfileContextResult result
    ) {
        if (result != null && result.isFailed()) {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("available", false);
            payload.put("errorCode", result.getErrorCode());
            agentSessionService.recordTrace(
                    userId,
                    sessionId,
                    TRACE_MEMORY_CONTEXT_LOADED,
                    "{}",
                    writeJson(payload),
                    "FAILED",
                    null,
                    result.getErrorCode()
            );
            return;
        }
        String status = result != null && result.isAvailable() ? "SUCCESS" : "EMPTY";
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("available", result != null && result.isAvailable());
        if (result != null && result.isAvailable()) {
            payload.put("weaknessCount", result.getWeaknessCount());
            payload.put("skillCount", result.getSkillCount());
            payload.put("hasSessionSummary", result.isHasSessionSummary());
        }
        agentSessionService.recordTrace(
                userId,
                sessionId,
                TRACE_MEMORY_CONTEXT_LOADED,
                "{}",
                writeJson(payload),
                status,
                null,
                null
        );
    }

    private void refreshConversationSummarySafely(Long userId, String sessionId) {
        try {
            agentMemoryService.refreshConversationSummary(userId, sessionId);
        } catch (Exception e) {
            log.warn("Failed to refresh conversation summary: userId={}, sessionId={}", userId, sessionId, e);
        }
    }

    private void recordPromptTemplateTrace(Long userId, String sessionId, String promptId, String version) {
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("promptId", promptId);
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("promptId", promptId);
        response.put("version", version);
        agentSessionService.recordTrace(
                userId,
                sessionId,
                TRACE_PROMPT_TEMPLATE,
                writeJson(request),
                writeJson(response),
                "SUCCESS",
                null,
                null
        );
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
