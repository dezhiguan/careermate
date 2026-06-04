package com.careermate.observability;

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "management.tracing.enabled=true")
class TraceHeaderPropagatorTest {

    @Autowired
    private TraceHeaderPropagator traceHeaderPropagator;

    @Autowired
    private Tracer tracer;

    @Test
    void injectAddsW3cAndCustomHeaders() {
        MDC.put(MdcKeys.REQUEST_ID, "req-test-001");
        MDC.put(MdcKeys.SESSION_ID, "sess-test-001");
        try {
            var span = tracer.nextSpan().name("test.propagation").start();
            try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
                Map<String, String> carrier = new LinkedHashMap<>();
                traceHeaderPropagator.inject(carrier);

                assertThat(carrier.get(MdcKeys.HEADER_REQUEST_ID)).isEqualTo("req-test-001");
                assertThat(carrier.get(MdcKeys.HEADER_SESSION_ID)).isEqualTo("sess-test-001");
                boolean hasTraceparent = carrier.entrySet().stream()
                        .anyMatch(e -> "traceparent".equalsIgnoreCase(e.getKey())
                                && e.getValue() != null
                                && !e.getValue().isBlank());
                assertThat(hasTraceparent)
                        .as("carrier keys: %s", carrier.keySet())
                        .isTrue();
            } finally {
                span.end();
            }
        } finally {
            MDC.clear();
        }
    }
}
