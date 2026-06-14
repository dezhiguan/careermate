package com.careermate.observability;

import com.careermate.ragforge.RagForgeClient;
import com.careermate.ragforge.RagForgeProperties;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "management.tracing.enabled=true",
        "careermate.ragforge.enabled=true",
        "careermate.ragforge.jd-kb-id=16"
})
class RagForgeClientPropagationTest {

    private static HttpServer server;
    private static int serverPort;
    private static final AtomicReference<String> CAPTURED_SW8 = new AtomicReference<>();
    private static final AtomicReference<String> CAPTURED_TRACEPARENT = new AtomicReference<>();
    private static final AtomicReference<String> CAPTURED_REQUEST_ID = new AtomicReference<>();
    private static final AtomicReference<String> CAPTURED_RESPONSE_TRACE_ID = new AtomicReference<>();

    @Autowired
    private RagForgeClient ragForgeClient;

    @Autowired
    private Tracer tracer;

    @Autowired
    private TraceHeaderPropagator traceHeaderPropagator;

    @DynamicPropertySource
    static void ragForgeUrl(DynamicPropertyRegistry registry) {
        registry.add("careermate.ragforge.url", () -> "http://localhost:" + serverPort);
    }

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        serverPort = server.getAddress().getPort();
        server.createContext("/api/v1/search", exchange -> {
            CAPTURED_SW8.set(firstHeader(exchange.getRequestHeaders(), "sw8"));
            CAPTURED_TRACEPARENT.set(firstHeader(exchange.getRequestHeaders(), "traceparent"));
            CAPTURED_REQUEST_ID.set(firstHeader(exchange.getRequestHeaders(), MdcKeys.HEADER_REQUEST_ID));

            String echoedTraceId = resolveEchoTraceId(
                    CAPTURED_SW8.get(),
                    CAPTURED_TRACEPARENT.get(),
                    firstHeader(exchange.getRequestHeaders(), MdcKeys.HEADER_TRACE_ID)
            );
            CAPTURED_RESPONSE_TRACE_ID.set(echoedTraceId);

            byte[] body = "{\"code\":200,\"data\":{\"results\":[]}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add(MdcKeys.HEADER_TRACE_ID, echoedTraceId);
            exchange.getResponseHeaders().add(MdcKeys.HEADER_REQUEST_ID, CAPTURED_REQUEST_ID.get());
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void searchPropagatesBusinessTraceHeaders() {
        resetCaptured();
        MDC.put(MdcKeys.REQUEST_ID, "req-rag-1");
        var span = tracer.nextSpan().name("test.ragforge").start();
        String expectedTraceId = span.context().traceId();
        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            List<?> results = ragForgeClient.searchJd("hello", 3);
            assertThat(results).isEmpty();
            assertThat(CAPTURED_TRACEPARENT.get()).isNotBlank();
            assertThat(CAPTURED_TRACEPARENT.get()).contains(expectedTraceId);
            assertThat(CAPTURED_REQUEST_ID.get()).isEqualTo("req-rag-1");
            assertThat(CAPTURED_RESPONSE_TRACE_ID.get()).isEqualTo(expectedTraceId);
        } finally {
            span.end();
            MDC.clear();
        }
    }

    @Test
    void searchDeliversSw8WhenAgentSimulated() {
        resetCaptured();
        RagForgeProperties props = new RagForgeProperties();
        props.setEnabled(true);
        props.setUrl("http://localhost:" + serverPort);
        props.setApiKey("k");
        props.setJdKbId("16");

        var span = tracer.nextSpan().name("test.ragforge.sw8").start();
        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            RagForgeClient clientWithSw8 = new RagForgeClient(
                    props,
                    traceHeaderPropagator,
                    Sw8SimulatingRestTemplateFactory.create(tracer, traceHeaderPropagator)
            );
            clientWithSw8.searchJd("hello", 3);
            assertThat(CAPTURED_SW8.get()).isNotBlank();
            assertThat(CAPTURED_TRACEPARENT.get()).isNotBlank();
        } finally {
            span.end();
        }
    }

    private static void resetCaptured() {
        CAPTURED_SW8.set(null);
        CAPTURED_TRACEPARENT.set(null);
        CAPTURED_REQUEST_ID.set(null);
        CAPTURED_RESPONSE_TRACE_ID.set(null);
    }

    private static String firstHeader(com.sun.net.httpserver.Headers headers, String name) {
        return headers.getFirst(name);
    }

    /**
     * Mirrors RAGForge {@code TraceIds.resolve}: prefer SkyWalking context (sw8 on server),
     * then W3C trace id from traceparent for dev/test without agent.
     */
    private static String resolveEchoTraceId(String sw8, String traceparent, String incomingTraceId) {
        if (incomingTraceId != null && !incomingTraceId.isBlank()) {
            return incomingTraceId.trim();
        }
        if (traceparent != null && traceparent.contains("-")) {
            String[] parts = traceparent.split("-");
            if (parts.length >= 2 && !parts[1].isBlank()) {
                return parts[1];
            }
        }
        return sw8 == null ? "rf-test" : "sw8-" + sw8.hashCode();
    }
}
