package com.careermate.ragforge;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagForgeClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void disabledReturnsEmpty() {
        RagForgeProperties props = new RagForgeProperties();
        props.setEnabled(false);
        props.setUrl("http://x");
        props.setApiKey("k");
        props.setJdKbId("16");
        RagForgeClient client = new RagForgeClient(props);
        assertEquals(List.of(), client.searchJd("test", 5));
    }

    @Test
    void emptyJdKbIdReturnsEmpty() {
        RagForgeProperties props = new RagForgeProperties();
        props.setEnabled(true);
        props.setJdKbId("");
        RagForgeClient client = new RagForgeClient(props);
        assertEquals(List.of(), client.searchJd("test", 5));
    }

    @Test
    void httpErrorReturnsEmptyNotThrow() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/api/v1/search", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();

        RagForgeProperties props = new RagForgeProperties();
        props.setEnabled(true);
        props.setUrl("http://localhost:" + port);
        props.setApiKey("k");
        props.setJdKbId("16");
        RagForgeClient client = new RagForgeClient(props);
        assertDoesNotThrow(() -> assertEquals(List.of(), client.searchJd("test", 5)));
    }

    @Test
    void syncTextAcceptsRagForgeCode200() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/api/v1/documents/text", exchange -> {
            byte[] body =
                "{\"code\":200,\"msg\":\"success\",\"data\":{\"docId\":42,\"skipped\":false}}"
                    .getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        RagForgeProperties props = new RagForgeProperties();
        props.setEnabled(true);
        props.setUrl("http://localhost:" + port);
        props.setApiKey("k");
        RagForgeClient client = new RagForgeClient(props);
        Optional<Long> docId = client.syncText(15L, "测试简历", "内容", "RESUME");
        assertTrue(docId.isPresent());
        assertEquals(42L, docId.get());
    }
}
