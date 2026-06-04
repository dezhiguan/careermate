package com.careermate.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class AgentTracing {

    private final Tracer tracer;

    public AgentTracing(Tracer tracer) {
        this.tracer = tracer;
    }

    public void run(String spanName, Long userId, String sessionId, Runnable runnable) {
        call(spanName, userId, sessionId, null, null, null, () -> {
            runnable.run();
            return null;
        });
    }

    public <T> T call(
            String spanName,
            Long userId,
            String sessionId,
            String toolName,
            String provider,
            String model,
            Supplier<T> supplier
    ) {
        Span span = tracer.nextSpan().name(spanName);
        applyTags(span, userId, sessionId, toolName, provider, model);
        try (Tracer.SpanInScope scope = tracer.withSpan(span.start())) {
            return supplier.get();
        } catch (RuntimeException | Error e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    public void runStream(Long userId, String sessionId, Runnable runnable) {
        run("agent.stream", userId, sessionId, runnable);
    }

    private void applyTags(
            Span span,
            Long userId,
            String sessionId,
            String toolName,
            String provider,
            String model
    ) {
        if (userId != null) {
            span.tag("user.id", String.valueOf(userId));
        }
        if (StringUtils.hasText(sessionId)) {
            span.tag("agent.session_id", sessionId);
        }
        if (StringUtils.hasText(toolName)) {
            span.tag("agent.tool_name", toolName);
        }
        if (StringUtils.hasText(provider)) {
            span.tag("agent.provider", provider);
        }
        if (StringUtils.hasText(model)) {
            span.tag("agent.model", model);
        }
    }

    public Map<String, String> agentTags(Long userId, String sessionId) {
        Map<String, String> tags = new LinkedHashMap<>();
        if (userId != null) {
            tags.put("user.id", String.valueOf(userId));
        }
        if (StringUtils.hasText(sessionId)) {
            tags.put("agent.session_id", sessionId);
        }
        return tags;
    }
}
