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
            log.warn("SSE error: sessionId={}", sessionId, error);
            taskRegistry.cancel(sessionId);
            cleanup(sessionId, emitter);
        });

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
