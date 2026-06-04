package com.careermate.observability;

import com.careermate.observability.ragforge.RagForgeClient;
import com.careermate.observability.ragforge.RagForgeSearchRequest;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "management.tracing.enabled=true",
        "careermate.ragforge.enabled=true"
})
class RagForgeClientPropagationTest {

    private static HttpServer server;
    private static int serverPort;
    private static final AtomicReference<String> CAPTURED_TRACEPARENT = new AtomicReference<>();

    @Autowired
    private RagForgeClient ragForgeClient;

    @Autowired
    private Tracer tracer;

    @DynamicPropertySource
    static void ragForgeUrl(DynamicPropertyRegistry registry) {
        registry.add("careermate.ragforge.url", () -> "http://localhost:" + serverPort);
    }

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        serverPort = server.getAddress().getPort();
        server.createContext("/api/rag/search", exchange -> {
            CAPTURED_TRACEPARENT.set(exchange.getRequestHeaders().getFirst("traceparent"));
            byte[] body = "{\"data\":{\"items\":[]}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
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
    void searchPropagatesTraceparent() {
        CAPTURED_TRACEPARENT.set(null);
        MDC.put(MdcKeys.REQUEST_ID, "req-rag-1");
        var span = tracer.nextSpan().name("test.ragforge").start();
        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            ragForgeClient.search(RagForgeSearchRequest.builder()
                    .kbId("kb-1")
                    .query("hello")
                    .topK(3)
                    .searchType("hybrid")
                    .build());
            assertThat(CAPTURED_TRACEPARENT.get()).isNotBlank();
        } finally {
            span.end();
            MDC.clear();
        }
    }
}
