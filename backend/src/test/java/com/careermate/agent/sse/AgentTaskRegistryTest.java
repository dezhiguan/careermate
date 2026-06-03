package com.careermate.agent.sse;

import com.careermate.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentTaskRegistryTest {

    @Test
    void startOrThrowShouldRejectWhenAlreadyRunning() {
        AgentTaskRegistry registry = new AgentTaskRegistry();

        CompletableFuture<Void> running = new CompletableFuture<>();
        assertDoesNotThrow(() -> registry.startOrThrow("s1", running));

        CompletableFuture<Void> another = new CompletableFuture<>();
        assertThrows(BizException.class, () -> registry.startOrThrow("s1", another));
    }

    @Test
    void startOrThrowAllowsReplaceWhenPreviousDone() {
        AgentTaskRegistry registry = new AgentTaskRegistry();

        CompletableFuture<Void> done = CompletableFuture.completedFuture(null);
        assertDoesNotThrow(() -> registry.startOrThrow("s1", done));

        CompletableFuture<Void> next = new CompletableFuture<>();
        assertDoesNotThrow(() -> registry.startOrThrow("s1", next));
    }
}
