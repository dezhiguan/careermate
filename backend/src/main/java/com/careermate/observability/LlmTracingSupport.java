package com.careermate.observability;

import com.careermate.llm.LlmProperties;
import com.careermate.llm.dto.ChatResponse;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;

@Slf4j
@Component
public class LlmTracingSupport {

    private final Tracer tracer;
    private final LlmProperties llmProperties;

    public LlmTracingSupport(Tracer tracer, LlmProperties llmProperties) {
        this.tracer = tracer;
        this.llmProperties = llmProperties;
    }

    public <T> T traceChat(String provider, String model, boolean stream, java.util.function.Supplier<T> supplier) {
        String resolvedProvider = StringUtils.hasText(provider) ? provider : llmProperties.getProvider();
        String resolvedModel = StringUtils.hasText(model) ? model : llmProperties.getModel();
        String endpointHost = resolveEndpointHost();

        Span span = tracer.nextSpan().name("llm.chat")
                .tag("llm.provider", safeTag(resolvedProvider))
                .tag("llm.model", safeTag(resolvedModel))
                .tag("llm.endpoint_host", safeTag(endpointHost))
                .tag("llm.stream", String.valueOf(stream))
                .tag("llm.timeout_ms", String.valueOf(llmProperties.getTimeoutMs()));

        long start = System.currentTimeMillis();
        try (Tracer.SpanInScope scope = tracer.withSpan(span.start())) {
            T result = supplier.get();
            long latencyMs = System.currentTimeMillis() - start;
            span.tag("llm.success", "true");
            span.tag("llm.latency_ms", String.valueOf(latencyMs));
            if (result instanceof ChatResponse response) {
                logOutcome(response, true, null, latencyMs);
            }
            return result;
        } catch (RuntimeException | Error e) {
            long latencyMs = System.currentTimeMillis() - start;
            span.tag("llm.success", "false");
            span.tag("llm.latency_ms", String.valueOf(latencyMs));
            log.error("llm.chat provider={} model={} latencyMs={} success=false errorCode={}",
                    resolvedProvider,
                    resolvedModel,
                    latencyMs,
                    safeTag(e.getClass().getSimpleName()));
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    public void logOutcome(ChatResponse response, boolean success, String errorCode, long latencyMs) {
        log.info(
                "llm.chat provider={} model={} latencyMs={} inputTokens={} outputTokens={} success={} errorCode={}",
                response == null ? llmProperties.getProvider() : response.getProvider(),
                response == null ? llmProperties.getModel() : response.getModel(),
                response != null && response.getLatencyMs() != null ? response.getLatencyMs() : latencyMs,
                response == null ? null : response.getInputTokens(),
                response == null ? null : response.getOutputTokens(),
                success,
                errorCode
        );
    }

    private String resolveEndpointHost() {
        String endpoint = llmProperties.getEndpoint();
        if (!StringUtils.hasText(endpoint)) {
            return "n/a";
        }
        try {
            URI uri = URI.create(endpoint.trim());
            return uri.getHost() == null ? "n/a" : uri.getHost();
        } catch (Exception e) {
            return "n/a";
        }
    }

    private static String safeTag(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 200 ? value.substring(0, 200) : value;
    }

}
