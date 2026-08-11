package com.careermate.agent.sse;

import com.careermate.agent.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SseEmitterService {

    /** 错误流只需活到把一条 error 事件写出去，给足网络抖动余量即可。 */
    private static final long ERROR_STREAM_TIMEOUT_MS = 10_000L;

    private final AgentProperties agentProperties;
    private final SseConnectionRegistry connectionRegistry;
    private final AgentTaskRegistry taskRegistry;
    private final ConcurrentHashMap.KeySetView<SseEmitter, Boolean> closedEmitters = ConcurrentHashMap.newKeySet();

    public SseEmitterService(
            AgentProperties agentProperties,
            SseConnectionRegistry connectionRegistry,
            AgentTaskRegistry taskRegistry
    ) {
        this.agentProperties = agentProperties;
        this.connectionRegistry = connectionRegistry;
        this.taskRegistry = taskRegistry;
    }

    public SseEmitter createEmitter(String sessionId) {
        SseEmitter emitter = new SseEmitter(agentProperties.getSseTimeoutMs());
        connectionRegistry.register(sessionId, emitter);

        emitter.onCompletion(() -> {
            log.info("SSE completed: sessionId={}", sessionId);
            cleanup(sessionId, emitter);
        });

        emitter.onTimeout(() -> {
            log.warn("SSE timeout: sessionId={}", sessionId);
            try {
                sendToEmitter(sessionId, emitter, SseEventType.ERROR, java.util.Map.of("message", "SSE 连接超时"));
            } catch (Exception ignored) {
            }
            taskRegistry.cancel(sessionId);
            safeComplete(sessionId, emitter);
            cleanup(sessionId, emitter);
        });

        emitter.onError((Throwable error) -> {
            // U3 断点续传：客户端断开不取消 server-side 任务，让 onComplete 完成持久化
            log.warn("SSE error (task continues): sessionId={}", sessionId, error);
            cleanup(sessionId, emitter);
        });

        return emitter;
    }

    /**
     * 前置校验失败时的一次性错误流。
     *
     * <p>SSE 端点的返回类型是 {@link SseEmitter}，方法在返回 emitter 之前抛出的异常无法被
     * {@code @RestControllerAdvice} 序列化成 JSON，只会退化成 HTTP 500 空响应体——前端拿不到
     * 任何提示，线上也没有 traceId 可查。改为正常建流、立刻下发 error 事件再结束，前端走既有
     * error 分支即可把原因展示给用户。
     *
     * <p>刻意不注册进 {@code connectionRegistry}：这条流不属于任何会话，注册会顶掉同会话的正常连接。
     */
    public SseEmitter errorStream(String message) {
        SseEmitter emitter = new SseEmitter(ERROR_STREAM_TIMEOUT_MS);
        try {
            String name = SseEventType.ERROR.name().toLowerCase(Locale.ROOT);
            emitter.send(SseEmitter.event().name(name).data(SseEvent.builder()
                    .type(name)
                    .data(java.util.Map.of("message", message))
                    .timestamp(System.currentTimeMillis())
                    .build()));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    public void send(String sessionId, SseEventType type, Object data) {
        connectionRegistry.get(sessionId).ifPresent(emitter -> {
            try {
                sendToEmitter(sessionId, emitter, type, data);
            } catch (IOException e) {
                log.info("SSE send failed (client gone): sessionId={}, type={}", sessionId, type);
                taskRegistry.cancel(sessionId);
                safeComplete(sessionId, emitter);
                cleanup(sessionId, emitter);
            } catch (Exception e) {
                log.warn("SSE send failed: sessionId={}, type={}", sessionId, type, e);
                taskRegistry.cancel(sessionId);
                safeCompleteWithError(sessionId, emitter, e);
                cleanup(sessionId, emitter);
            }
        });
    }

    public void complete(String sessionId) {
        connectionRegistry.get(sessionId).ifPresent(emitter -> {
            safeComplete(sessionId, emitter);
            cleanup(sessionId, emitter);
        });
    }

    public void completeWithError(String sessionId, Throwable error) {
        connectionRegistry.get(sessionId).ifPresent(emitter -> {
            safeCompleteWithError(sessionId, emitter, error);
            cleanup(sessionId, emitter);
        });
    }

    private void sendToEmitter(String sessionId, SseEmitter emitter, SseEventType type, Object data) throws IOException {
        String name = type.name().toLowerCase(Locale.ROOT);
        SseEvent event = SseEvent.builder()
                .type(name)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
        emitter.send(SseEmitter.event().name(name).data(event));
    }

    private void safeComplete(String sessionId, SseEmitter emitter) {
        if (!markClosed(emitter)) {
            return;
        }
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }

    private void safeCompleteWithError(String sessionId, SseEmitter emitter, Throwable error) {
        if (!markClosed(emitter)) {
            return;
        }
        try {
            emitter.completeWithError(error);
        } catch (Exception ignored) {
        }
    }

    private void cleanup(String sessionId, SseEmitter emitter) {
        connectionRegistry.remove(sessionId, emitter);
        closedEmitters.remove(emitter);
    }

    private boolean markClosed(SseEmitter emitter) {
        return closedEmitters.add(emitter);
    }
}
