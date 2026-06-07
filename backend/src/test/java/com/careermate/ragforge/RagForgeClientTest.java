package com.careermate.ragforge;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
