package com.careermate.agent.controller;

import com.careermate.agent.AgentPromptAssembler;
import com.careermate.agent.dto.AgentMessageRequest;
import com.careermate.agent.dto.AgentSessionCreateResponse;
import com.careermate.agent.dto.AgentSessionResponse;
import com.careermate.agent.dto.AgentTraceResponse;
import com.careermate.agent.session.AgentSessionService;
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
import com.careermate.llm.StreamCallback;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.security.CurrentUserContext;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
    private final ObjectMapper objectMapper;

    private static final String TRACE_RESUME_CONTEXT = "resume_context";

    public AgentStreamController(
            LlmClient llmClient,
            TaskExecutor agentExecutor,
            SseEmitterService sseEmitterService,
            AgentTaskRegistry taskRegistry,
            AgentSessionService agentSessionService,
            ResumeContextProvider resumeContextProvider,
            ObjectMapper objectMapper
    ) {
        this.llmClient = llmClient;
        this.agentExecutor = agentExecutor;
        this.sseEmitterService = sseEmitterService;
        this.taskRegistry = taskRegistry;
        this.agentSessionService = agentSessionService;
        this.resumeContextProvider = resumeContextProvider;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/sessions")
    public ApiResponse<AgentSessionCreateResponse> createSession() {
        Long userId = CurrentUserContext.getUserId();
        return ApiResponse.success(agentSessionService.createSession(userId));
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

        if (taskRegistry.isRunning(sessionId)) {
            throw new BizException(429, "当前会话已有任务运行中");
        }

        agentSessionService.getSession(userId, sessionId);

        SseEmitter emitter = sseEmitterService.createEmitter(sessionId);

        FutureTask<Void> task = new FutureTask<>(() -> {
            runStreamingTask(userId, sessionId, request);
            return null;
        });
        taskRegistry.tryStart(sessionId, task);

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

        try {
            agentSessionService.appendMessage(userId, sessionId, "user", request.getMessage(), "text");

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

            ResumeContext resumeContext = resumeContextProvider.getResumeContext(userId);
            recordResumeContextTrace(userId, sessionId, resumeContext);

            String systemPrompt = AgentPromptAssembler.buildSystemPrompt(resumeContext);
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(List.of(
                            ChatMessage.builder().role("system").content(systemPrompt).build(),
                            ChatMessage.builder().role("user").content(request.getMessage()).build()
                    ))
                    .build();

            llmClient.streamChat(chatRequest, new StreamCallback() {
                @Override
                public void onToken(String token) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new RuntimeException("cancelled");
                    }
                    if (token == null || token.isEmpty()) {
                        return;
                    }
                    full.append(token);
                    sseEmitterService.send(sessionId, SseEventType.TOKEN, Map.of("content", token));
                }

                @Override
                public void onComplete(ChatResponse response) {
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
                }

                @Override
                public void onError(Throwable error) {
                    handleStreamError(userId, sessionId, error, "LLM_ERROR");
                }
            });
        } catch (Throwable t) {
            handleStreamError(userId, sessionId, t, "SSE_ERROR");
        } finally {
            try {
                heartbeatFuture.cancel(true);
            } catch (Exception ignored) {
            }
            heartbeatExecutor.shutdownNow();
        }
    }

    private void handleStreamError(Long userId, String sessionId, Throwable error, String errorCode) {
        String message = error == null ? "未知错误" : (error.getMessage() == null ? "未知错误" : error.getMessage());
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
        sseEmitterService.completeWithError(sessionId, error == null ? new RuntimeException("unknown") : error);
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
