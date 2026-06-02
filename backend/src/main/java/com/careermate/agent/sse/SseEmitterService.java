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

    private final AgentProperties agentProperties;
    private final SseConnectionRegistry connectionRegistry;
    private final AgentTaskRegistry taskRegistry;
    private final ConcurrentHashMap.KeySetView<String, Boolean> closedSessions = ConcurrentHashMap.newKeySet();

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
            cleanup(sessionId);
        });

        emitter.onTimeout(() -> {
            log.warn("SSE timeout: sessionId={}", sessionId);
            try {
                send(sessionId, SseEventType.ERROR, java.util.Map.of("message", "SSE 连接超时"));
            } catch (Exception ignored) {
            }
            taskRegistry.cancel(sessionId);
            safeComplete(sessionId);
            cleanup(sessionId);
        });

        emitter.onError((Throwable error) -> {
            log.warn("SSE error: sessionId={}", sessionId, error);
            taskRegistry.cancel(sessionId);
            cleanup(sessionId);
        });

        return emitter;
    }

    public void send(String sessionId, SseEventType type, Object data) {
        connectionRegistry.get(sessionId).ifPresent(emitter -> {
            String name = type.name().toLowerCase(Locale.ROOT);
            SseEvent event = SseEvent.builder()
                    .type(name)
                    .data(data)
                    .timestamp(System.currentTimeMillis())
                    .build();
            try {
                emitter.send(SseEmitter.event().name(name).data(event));
            } catch (IOException e) {
                log.info("SSE send failed (client gone): sessionId={}, type={}", sessionId, type);
                taskRegistry.cancel(sessionId);
                safeComplete(sessionId);
                cleanup(sessionId);
            } catch (Exception e) {
                log.warn("SSE send failed: sessionId={}, type={}", sessionId, type, e);
                taskRegistry.cancel(sessionId);
                safeCompleteWithError(sessionId, e);
                cleanup(sessionId);
            }
        });
    }

    public void complete(String sessionId) {
        safeComplete(sessionId);
        cleanup(sessionId);
    }

    public void completeWithError(String sessionId, Throwable error) {
        safeCompleteWithError(sessionId, error);
        cleanup(sessionId);
    }

    private void safeComplete(String sessionId) {
        if (!markClosed(sessionId)) {
            return;
        }
        connectionRegistry.get(sessionId).ifPresent(emitter -> {
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        });
    }

    private void safeCompleteWithError(String sessionId, Throwable error) {
        if (!markClosed(sessionId)) {
            return;
        }
        connectionRegistry.get(sessionId).ifPresent(emitter -> {
            try {
                emitter.completeWithError(error);
            } catch (Exception ignored) {
            }
        });
    }

    private void cleanup(String sessionId) {
        connectionRegistry.remove(sessionId);
        taskRegistry.complete(sessionId);
        closedSessions.remove(sessionId);
    }

    private boolean markClosed(String sessionId) {
        return closedSessions.add(sessionId);
    }
}

