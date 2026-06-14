package com.careermate.observability;

import io.micrometer.tracing.Tracer;
import org.springframework.web.client.RestTemplate;

/**
 * Test helper: simulates SkyWalking agent injecting {@code sw8} at HTTP send time.
 */
final class Sw8SimulatingRestTemplateFactory {

    private Sw8SimulatingRestTemplateFactory() {
    }

    static RestTemplate create(Tracer tracer, TraceHeaderPropagator traceHeaderPropagator) {
        RestTemplate template = new RestTemplate();
        if (traceHeaderPropagator != null) {
            template.getInterceptors().add(traceHeaderPropagator.clientHttpRequestInterceptor());
        }
        template.getInterceptors().add((request, body, execution) -> {
            var span = tracer.currentSpan();
            String traceId = span == null ? "mock-trace" : span.context().traceId();
            String spanId = span == null ? "mock-span" : span.context().spanId();
            request.getHeaders().set("sw8", "1-" + traceId + "-" + spanId + "-0-EN0x0-0-");
            return execution.execute(request, body);
        });
        return template;
    }
}
