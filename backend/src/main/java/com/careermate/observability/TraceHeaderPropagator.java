package com.careermate.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.http.HttpRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Propagates W3C trace context and CareerMate auxiliary headers to outbound HTTP calls (e.g. RAGForge).
 */
@Component
public class TraceHeaderPropagator {

    private final Tracer tracer;
    private final Propagator propagator;
    private final TraceIdResolver traceIdResolver;

    public TraceHeaderPropagator(Tracer tracer, Propagator propagator, TraceIdResolver traceIdResolver) {
        this.tracer = tracer;
        this.propagator = propagator;
        this.traceIdResolver = traceIdResolver;
    }

    public void inject(HttpRequest.Builder builder) {
        if (builder == null) {
            return;
        }
        Map<String, String> carrier = new LinkedHashMap<>();
        inject(carrier);
        carrier.forEach(builder::header);
    }

    public void inject(Map<String, String> carrier) {
        if (carrier == null) {
            return;
        }
        TraceContext context = currentTraceContext();
        if (context != null) {
            propagator.inject(context, carrier, Map::put);
            if (!hasTraceparent(carrier)) {
                carrier.put("traceparent", formatTraceparent(context));
            }
        }
        String requestId = MDC.get(MdcKeys.REQUEST_ID);
        if (StringUtils.hasText(requestId)) {
            carrier.putIfAbsent(MdcKeys.HEADER_REQUEST_ID, requestId);
        }
        String sessionId = MDC.get(MdcKeys.SESSION_ID);
        if (StringUtils.hasText(sessionId)) {
            carrier.putIfAbsent(MdcKeys.HEADER_SESSION_ID, sessionId);
        }
    }

    public void inject(BiConsumer<String, String> headerSetter) {
        if (headerSetter == null) {
            return;
        }
        Map<String, String> carrier = new LinkedHashMap<>();
        inject(carrier);
        carrier.forEach(headerSetter);
    }

    public String currentTraceId() {
        String resolved = traceIdResolver.resolveTraceId();
        if (StringUtils.hasText(resolved)) {
            return resolved;
        }
        TraceContext context = currentTraceContext();
        if (context != null && StringUtils.hasText(context.traceId())) {
            return context.traceId();
        }
        String mdcTraceId = MDC.get(MdcKeys.TRACE_ID);
        if (StringUtils.hasText(mdcTraceId)) {
            return mdcTraceId;
        }
        return MDC.get(MdcKeys.REQUEST_ID);
    }

    private TraceContext currentTraceContext() {
        Span span = tracer.currentSpan();
        return span == null ? null : span.context();
    }

    private static boolean hasTraceparent(Map<String, String> carrier) {
        return carrier.entrySet().stream()
                .anyMatch(e -> "traceparent".equalsIgnoreCase(e.getKey())
                        && StringUtils.hasText(e.getValue()));
    }

    private static String formatTraceparent(TraceContext context) {
        return "00-" + context.traceId() + "-" + context.spanId() + "-01";
    }
}
