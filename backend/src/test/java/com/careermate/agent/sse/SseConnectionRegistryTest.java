package com.careermate.agent.sse;

import com.careermate.agent.config.AgentProperties;
import com.careermate.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SseConnectionRegistryTest {

    @Test
    void registerShouldRejectWhenOverCapacity() {
        AgentProperties properties = new AgentProperties();
        properties.setMaxConcurrentSessions(1);
        SseConnectionRegistry registry = new SseConnectionRegistry(properties);

        assertDoesNotThrow(() -> registry.register("s1", new SseEmitter(1000L)));
        assertThrows(BizException.class, () -> registry.register("s2", new SseEmitter(1000L)));
    }
}

