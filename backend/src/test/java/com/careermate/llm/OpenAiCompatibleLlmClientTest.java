package com.careermate.llm;

import com.careermate.common.exception.BizException;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.llm.dto.ToolCallRequest;
import com.careermate.llm.dto.ToolCallResponse;
import com.careermate.llm.provider.OpenAiCompatibleLlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleLlmClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private LlmProperties properties;
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        properties = new LlmProperties();
        properties.setApiKey("sk-test");
        properties.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/");
        properties.setModel("default-model");
        properties.setTemperature(0.2);
        properties.setMaxTokens(128);
        properties.setTimeoutMs(5000L);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void chatPostsOpenAiPayloadAndMapsResponse() {
        AtomicReference<JsonNode> captured = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            captured.set(objectMapper.readTree(readBody(exchange)));
            respond(exchange, 200, """
                    {"choices":[{"message":{"content":"hello from llm"},"finish_reason":"stop"}],"usage":{"prompt_tokens":7,"completion_tokens":3}}
                    """, "application/json");
        });

        ChatResponse response = client().chat(ChatRequest.builder()
                .model("override-model")
                .temperature(0.5)
                .maxTokens(64)
                .messages(List.of(
                        ChatMessage.builder().role("system").content("sys").build(),
                        ChatMessage.builder().role("user").content("hi").build()))
                .build());

        assertEquals("hello from llm", response.getContent());
        assertEquals("override-model", response.getModel());
        assertEquals("openai-compatible", response.getProvider());
        assertEquals(7, response.getInputTokens());
        assertEquals(3, response.getOutputTokens());
        assertEquals("stop", response.getFinishReason());
        assertEquals("Bearer sk-test", lastAuthorization.get());
        assertEquals("override-model", captured.get().path("model").asText());
        assertEquals("hi", captured.get().path("messages").get(1).path("content").asText());
        assertEquals(false, captured.get().path("stream").asBoolean());
    }

    @Test
    void chatMapsHttpErrorToBizException() {
        server.createContext("/v1/chat/completions", exchange ->
                respond(exchange, 401, "{\"error\":\"bad key\"}", "application/json"));

        BizException ex = org.junit.jupiter.api.Assertions.assertThrows(
                BizException.class,
                () -> client().chat(ChatRequest.builder()
                        .messages(List.of(ChatMessage.builder().role("user").content("hi").build()))
                        .build()));

        assertEquals(500, ex.getCode());
        assertTrue(ex.getMessage().contains("API Key") || ex.getMessage().contains("LLM"));
    }

    @Test
    void streamChatEmitsTokensAndCompletion() {
        server.createContext("/v1/chat/completions", exchange -> respond(exchange, 200, """
                data: {"choices":[{"delta":{"content":"你"},"finish_reason":null}]}

                data: not-json

                data: {"choices":[{"delta":{"content":"好"},"finish_reason":"stop"}],"usage":{"prompt_tokens":2,"completion_tokens":2}}

                data: [DONE]

                """, "text/event-stream"));

        List<String> tokens = new ArrayList<>();
        AtomicReference<ChatResponse> completed = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        client().streamChat(ChatRequest.builder()
                .messages(List.of(ChatMessage.builder().role("user").content("stream").build()))
                .build(), callback(tokens, completed, error));

        assertEquals(List.of("你", "好"), tokens);
        assertEquals("你好", completed.get().getContent());
        assertEquals("stop", completed.get().getFinishReason());
        assertEquals(2, completed.get().getInputTokens());
        assertEquals(2, completed.get().getOutputTokens());
        assertEquals(null, error.get());
    }

    @Test
    void streamChatReportsHttpErrorThroughCallback() {
        server.createContext("/v1/chat/completions", exchange ->
                respond(exchange, 429, "{\"error\":\"rate limit\"}", "application/json"));
        AtomicReference<Throwable> error = new AtomicReference<>();

        client().streamChat(ChatRequest.builder()
                .messages(List.of(ChatMessage.builder().role("user").content("stream").build()))
                .build(), callback(new ArrayList<>(), new AtomicReference<>(), error));

        assertInstanceOf(BizException.class, error.get());
        assertEquals(500, ((BizException) error.get()).getCode());
    }

    @Test
    void validatesConfigAndToolCallFallback() {
        properties.setApiKey("");
        BizException ex = org.junit.jupiter.api.Assertions.assertThrows(
                BizException.class,
                () -> client().chat(ChatRequest.builder().build()));
        assertEquals(400, ex.getCode());

        properties.setApiKey("sk-test");
        ToolCallResponse toolCall = client().toolCall(ToolCallRequest.builder().model("tool-model").build());
        assertEquals("tool-model", toolCall.getModel());
        assertEquals("openai-compatible", toolCall.getProvider());
        assertTrue(toolCall.getToolCalls().isEmpty());
    }

    private OpenAiCompatibleLlmClient client() {
        return new OpenAiCompatibleLlmClient(properties, objectMapper, "openai-compatible");
    }

    private StreamCallback callback(List<String> tokens, AtomicReference<ChatResponse> completed, AtomicReference<Throwable> error) {
        return new StreamCallback() {
            @Override
            public void onToken(String token) {
                tokens.add(token);
            }

            @Override
            public void onComplete(ChatResponse response) {
                completed.set(response);
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
            }
        };
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
