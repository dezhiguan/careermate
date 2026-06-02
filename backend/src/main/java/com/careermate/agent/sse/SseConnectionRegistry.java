package com.careermate.agent.sse;

import com.careermate.agent.config.AgentProperties;
import com.careermate.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SseConnectionRegistry {

    private final AgentProperties agentProperties;
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseConnectionRegistry(AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
    }

    public void register(String sessionId, SseEmitter emitter) {
        if (size() >= agentProperties.getMaxConcurrentSessions() && !exists(sessionId)) {
            throw new BizException(429, "SSE 连接数过多");
        }

        SseEmitter old = emitters.put(sessionId, emitter);
        if (old != null) {
            try {
                old.complete();
            } catch (Exception e) {
                log.debug("Failed to complete old SSE emitter: sessionId={}", sessionId, e);
            }
        }
    }

    public Optional<SseEmitter> get(String sessionId) {
        return Optional.ofNullable(emitters.get(sessionId));
    }

    public void remove(String sessionId) {
        emitters.remove(sessionId);
    }

    public boolean exists(String sessionId) {
        return emitters.containsKey(sessionId);
    }

    public int size() {
        return emitters.size();
    }
}

