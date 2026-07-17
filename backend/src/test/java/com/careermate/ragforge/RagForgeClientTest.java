package com.careermate.ragforge;

import com.careermate.auth.gateway.AuthGatewayClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagForgeClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        RequestContextHolder.resetRequestAttributes();
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

    @Test
    void ingestTextRelayParsesDocumentIdAndSendsMultipartWithApiKey() throws IOException {
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> apiKeyHeader = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/api/v1/documents", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            apiKeyHeader.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"status\":\"CREATED\",\"documentId\":93052}".getBytes();
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

        Optional<Long> docId = client.ingestText(677L, "ltm-u7-abc123", "只考虑远程岗位", "PREFERENCE");

        assertTrue(docId.isPresent());
        assertEquals(93052L, docId.get());
        assertTrue(contentType.get() != null && contentType.get().startsWith("multipart/form-data"));
        assertEquals("k", apiKeyHeader.get());
    }

    @Test
    void ingestTextReturnsExistingDocIdOnConflict() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/api/v1/documents", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"error\":\"DOC_IDENTITY_CONFLICT\",\"existingDocId\":555}".getBytes();
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

        Optional<Long> docId = client.ingestText(677L, "ltm-u7-dup", "重复事实", "SKILL");
        assertEquals(Optional.of(555L), docId);
    }

    @Test
    void ingestTextDisabledOrBlankReturnsEmpty() {
        RagForgeProperties props = new RagForgeProperties();
        props.setEnabled(false);
        assertEquals(Optional.empty(), new RagForgeClient(props).ingestText(677L, "x", "内容", "PREFERENCE"));
        props.setEnabled(true);
        RagForgeClient client = new RagForgeClient(props);
        assertEquals(Optional.empty(), client.ingestText(null, "x", "内容", "PREFERENCE"));
        assertEquals(Optional.empty(), client.ingestText(677L, "x", "  ", "PREFERENCE"));
    }

    @Test
    void usesApiKeyHeaderWhenConfigured() throws IOException {
        AtomicReference<String> apiKeyHeader = new AtomicReference<>();
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/api/v1/search", exchange -> {
            apiKeyHeader.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"code\":200,\"msg\":\"success\",\"data\":{\"chunks\":[]}}".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        RagForgeProperties props = new RagForgeProperties();
        props.setEnabled(true);
        props.setUrl("http://localhost:" + port);
        props.setApiKey("test-api-key");
        props.setJdKbId("16");
        RagForgeClient client = new RagForgeClient(props);

        assertEquals(List.of(), client.searchJd("test", 5));
        assertEquals("test-api-key", apiKeyHeader.get());
        assertNull(authorizationHeader.get());
    }

    @Test
    void searchParsesResultsAndSendsChunkTypeFilter() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/api/v1/search", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = """
                    {"code":0,"data":{"results":[
                      {"chunkId":7,"docId":9,"filename":"jd.md","content":"Java JD","chunkType":"JD","finalScore":0.81},
                      {"filename":"empty.md","content":"No ids"}
                    ]}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        RagForgeClient client = new RagForgeClient(enabledProps(port));

        List<RagForgeChunk> chunks = client.search(16L, "Java", 3, List.of("JD", "MARKET"));

        assertEquals(2, chunks.size());
        assertEquals(7L, chunks.get(0).chunkId());
        assertEquals(9L, chunks.get(0).docId());
        assertEquals("jd.md", chunks.get(0).filename());
        assertEquals(0.81, chunks.get(0).finalScore());
        assertTrue(requestBody.get().contains("\"kbIds\":[16]"));
        assertTrue(requestBody.get().contains("\"chunkType\":[\"JD\",\"MARKET\"]"));
    }

    @Test
    void fetchDocumentChunksReadsMultiplePagesAndStopsOnTotal() throws IOException {
        AtomicReference<Integer> callCount = new AtomicReference<>(0);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/api/v1/documents/42/chunks", exchange -> {
            int count = callCount.updateAndGet(v -> v + 1);
            String json = count == 1
                    ? "{\"code\":200,\"data\":{\"total\":101,\"list\":[{\"chunkIndex\":1,\"content\":\"第一页\"}]}}"
                    : "{\"code\":200,\"data\":{\"total\":101,\"list\":[{\"chunkIndex\":2,\"content\":\"第二页\"}]}}";
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        RagForgeClient client = new RagForgeClient(enabledProps(port));

        List<RagForgeChunk> chunks = client.fetchDocumentChunks(42L);

        assertEquals(2, chunks.size());
        assertEquals("第一页", chunks.get(0).content());
        assertEquals("第二页", chunks.get(1).content());
        assertEquals(2, callCount.get());
        assertEquals(List.of(), client.fetchDocumentChunks(null));
        assertEquals(List.of(), client.fetchDocumentChunks(0L));
    }

    @Test
    void fetchDocumentChunksStopsOnBlankFailureOrEmptyList() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/api/v1/documents/1/chunks", exchange -> {
            byte[] body = "{\"code\":500,\"data\":{\"list\":[{\"content\":\"ignored\"}]}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/api/v1/documents/2/chunks", exchange -> {
            byte[] body = "{\"code\":200,\"data\":{\"list\":[]}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/api/v1/documents/3/chunks", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        RagForgeClient client = new RagForgeClient(enabledProps(port));

        assertEquals(List.of(), client.fetchDocumentChunks(1L));
        assertEquals(List.of(), client.fetchDocumentChunks(2L));
        assertEquals(List.of(), client.fetchDocumentChunks(3L));
    }

    @Test
    void syncTextAndDeleteDocumentHandleNoopsAndFailures() throws IOException {
        AtomicReference<String> deleteMethod = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/api/v1/documents/text", exchange -> {
            byte[] body = "{\"code\":500,\"data\":{\"docId\":1}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/api/v1/documents/5", exchange -> {
            deleteMethod.set(exchange.getRequestMethod());
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();

        RagForgeClient client = new RagForgeClient(enabledProps(port));

        assertEquals(Optional.empty(), client.syncText(null, "t", "content", "RESUME"));
        assertEquals(Optional.empty(), client.syncText(1L, "t", "  ", "RESUME"));
        assertEquals(Optional.empty(), client.syncText(1L, "t", "content", "RESUME"));
        client.deleteDocument(null);
        client.deleteDocument(5L);
        assertEquals("DELETE", deleteMethod.get());
    }

    @Test
    void invalidKbIdsAndInvalidSearchInputsReturnEmpty() {
        RagForgeProperties props = new RagForgeProperties();
        props.setEnabled(true);
        props.setJdKbId("bad");
        props.setInterviewKbId("bad");
        RagForgeClient client = new RagForgeClient(props);

        assertEquals(List.of(), client.searchJd("test", 5));
        assertEquals(List.of(), client.searchInterview("test", 5));
        assertEquals(List.of(), client.search(null, "test", 5, null));
        assertEquals(List.of(), client.search(1L, " ", 5, null));
    }

    @Test
    void exchangesBearerTokenWhenApiKeyIsAbsentAndCachesIt() throws IOException {
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/api/v1/search", exchange -> {
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"code\":200,\"data\":{\"results\":[]}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        RagForgeProperties props = enabledProps(port);
        props.setApiKey("");
        props.setExchangedTokenCacheTtlMs(60_000L);
        AuthGatewayClient authGatewayClient = mock(AuthGatewayClient.class);
        AuthGatewayClient.TokenExchangeResponse response = new AuthGatewayClient.TokenExchangeResponse();
        response.setAccessToken("rag-token");
        response.setExpiresIn(60L);
        when(authGatewayClient.tokenExchange("user-token", props.getRequestedAudience(), props.getRequestedScopes()))
                .thenReturn(response);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer user-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RagForgeClient client = new RagForgeClient(props, null, authGatewayClient, null);

        client.searchJd("Java", 5);
        client.searchJd("Java", 5);

        assertEquals("Bearer rag-token", authorizationHeader.get());
        verify(authGatewayClient, times(1))
                .tokenExchange("user-token", props.getRequestedAudience(), props.getRequestedScopes());
    }

    private static RagForgeProperties enabledProps(int port) {
        RagForgeProperties props = new RagForgeProperties();
        props.setEnabled(true);
        props.setUrl("http://localhost:" + port);
        props.setApiKey("k");
        props.setJdKbId("16");
        props.setInterviewKbId("21");
        props.setTimeoutMs(1500);
        return props;
    }
}
