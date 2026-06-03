package com.careermate.agent.sse;

import com.careermate.agent.config.AgentProperties;
import com.careermate.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SseConnectionRegistryTest {

    @Test
    void registerShouldRejectWhenOverCapacity() {
        AgentProperties properties = new AgentProperties();
        properties.setMaxConcurrentSessions(1);
        SseConnectionRegistry registry = new SseConnectionRegistry(properties);

        assertDoesNotThrow(() -> registry.register("s1", new SseEmitter(1000L)));
        assertThrows(BizException.class, () -> registry.register("s2", new SseEmitter(1000L)));
    }

    @Test
    void staleEmitterRemoveShouldNotRemoveCurrentEmitter() {
        AgentProperties properties = new AgentProperties();
        properties.setMaxConcurrentSessions(10);
        SseConnectionRegistry registry = new SseConnectionRegistry(properties);

        SseEmitter oldEmitter = new SseEmitter(1000L);
        SseEmitter currentEmitter = new SseEmitter(1000L);

        registry.register("s1", oldEmitter);
        registry.register("s1", currentEmitter);

        registry.remove("s1", oldEmitter);

        assertTrue(registry.get("s1").isPresent());
        assertSame(currentEmitter, registry.get("s1").orElseThrow());

        registry.remove("s1", currentEmitter);

        assertTrue(registry.get("s1").isEmpty());
    }
}
