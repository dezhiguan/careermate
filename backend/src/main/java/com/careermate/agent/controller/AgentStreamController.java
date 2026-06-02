package com.careermate.agent.controller;

import com.careermate.agent.dto.AgentMessageRequest;
import com.careermate.agent.dto.AgentSessionCreateResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    public AgentStreamController(
            LlmClient llmClient,
            TaskExecutor agentExecutor,
            SseEmitterService sseEmitterService,
            AgentTaskRegistry taskRegistry
    ) {
        this.llmClient = llmClient;
        this.agentExecutor = agentExecutor;
        this.sseEmitterService = sseEmitterService;
        this.taskRegistry = taskRegistry;
    }

    @PostMapping("/sessions")
    public ApiResponse<AgentSessionCreateResponse> createSession() {
        String raw = UUID.randomUUID().toString().replace("-", "");
        String sessionId = "s_" + raw.substring(0, 16);
        return ApiResponse.success(AgentSessionCreateResponse.builder().sessionId(sessionId).build());
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

        SseEmitter emitter = sseEmitterService.createEmitter(sessionId);

        FutureTask<Void> task = new FutureTask<>(() -> {
            runStreamingTask(sessionId, request);
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

    private void runStreamingTask(String sessionId, AgentMessageRequest request) {
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
            sseEmitterService.send(sessionId, SseEventType.PLAN, Map.of(
                    "steps", List.of("接收用户输入", "调用 LLM", "生成回复"),
                    "totalSteps", 3
            ));

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(List.of(
                            ChatMessage.builder().role("system").content("你是 CareerMate 求职智能体。").build(),
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
                    sseEmitterService.send(sessionId, SseEventType.DONE, Map.of(
                            "sessionId", sessionId,
                            "totalLatencyMs", System.currentTimeMillis() - start
                    ));
                    sseEmitterService.complete(sessionId);
                }

                @Override
                public void onError(Throwable error) {
                    sseEmitterService.send(sessionId, SseEventType.ERROR, Map.of(
                            "message", error == null ? "未知错误" : (error.getMessage() == null ? "未知错误" : error.getMessage())
                    ));
                    sseEmitterService.completeWithError(sessionId, error == null ? new RuntimeException("unknown") : error);
                }
            });
        } catch (Throwable t) {
            sseEmitterService.send(sessionId, SseEventType.ERROR, Map.of("message", t.getMessage() == null ? "系统异常" : t.getMessage()));
            sseEmitterService.completeWithError(sessionId, t);
        } finally {
            try {
                heartbeatFuture.cancel(true);
            } catch (Exception ignored) {
            }
            heartbeatExecutor.shutdownNow();
        }
    }
}

