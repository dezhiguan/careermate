package com.careermate.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Resolves the active trace id for logs and response headers.
 * Prefers SkyWalking Java Agent context when present; falls back to Micrometer.
 */
@Component
public class TraceIdResolver {

    private static final String SKYWALKING_IGNORED_TRACE = "Ignored_Trace";

    private final Tracer tracer;

    public TraceIdResolver(Tracer tracer) {
        this.tracer = tracer;
    }

    public String resolveTraceId() {
        String skyWalkingTraceId = org.apache.skywalking.apm.toolkit.trace.TraceContext.traceId();
        if (isUsableTraceId(skyWalkingTraceId)) {
            return skyWalkingTraceId;
        }
        io.micrometer.tracing.TraceContext micrometerContext = micrometerTraceContext();
        if (micrometerContext == null) {
            return null;
        }
        return micrometerContext.traceId();
    }

    public String resolveSpanId() {
        io.micrometer.tracing.TraceContext micrometerContext = micrometerTraceContext();
        if (micrometerContext != null && StringUtils.hasText(micrometerContext.spanId())) {
            return micrometerContext.spanId();
        }
        return null;
    }

    private io.micrometer.tracing.TraceContext micrometerTraceContext() {
        Span span = tracer.currentSpan();
        return span == null ? null : span.context();
    }

    private static boolean isUsableTraceId(String traceId) {
        return StringUtils.hasText(traceId)
                && !SKYWALKING_IGNORED_TRACE.equals(traceId)
                && !"N/A".equalsIgnoreCase(traceId);
    }
}
