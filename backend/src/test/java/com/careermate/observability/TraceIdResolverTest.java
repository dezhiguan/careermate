package com.careermate.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TraceIdResolverTest {

    @Mock
    private Tracer tracer;

    @Test
    void fallsBackToMicrometerWhenSkyWalkingTraceAbsent() {
        TraceContext context = mock(TraceContext.class);
        when(context.traceId()).thenReturn("micrometer-trace-abc");
        Span span = mock(Span.class);
        when(span.context()).thenReturn(context);
        when(tracer.currentSpan()).thenReturn(span);

        TraceIdResolver resolver = new TraceIdResolver(tracer);
        assertThat(resolver.resolveTraceId()).isEqualTo("micrometer-trace-abc");
    }
}
