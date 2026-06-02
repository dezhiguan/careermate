package com.careermate.agent.sse;

import com.careermate.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentTaskRegistryTest {

    @Test
    void tryStartShouldRejectWhenAlreadyRunning() {
        AgentTaskRegistry registry = new AgentTaskRegistry();

        CompletableFuture<Void> running = new CompletableFuture<>();
        assertDoesNotThrow(() -> registry.tryStart("s1", running));

        CompletableFuture<Void> another = new CompletableFuture<>();
        assertThrows(BizException.class, () -> registry.tryStart("s1", another));
    }
}

