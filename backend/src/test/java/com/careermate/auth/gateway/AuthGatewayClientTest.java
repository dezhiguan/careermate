package com.careermate.auth.gateway;

import com.careermate.common.exception.BizException;
import com.careermate.security.ClientAssertionFactory;
import com.careermate.security.SecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthGatewayClientTest {

    private HttpServer server;
    private AuthGatewayClient client;
    private final AtomicReference<String> lastBody = new AtomicReference<>("");
    private final AtomicReference<String> lastContentType = new AtomicReference<>("");

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();

        SecurityProperties securityProperties = new SecurityProperties();
        SecurityProperties.AuthGateway authGateway = securityProperties.getAuthGateway();
        authGateway.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        authGateway.setAudience("careermate-api");
        authGateway.setClientId("careermate-backend");
        authGateway.setTimeoutMs(5000);

        ClientAssertionFactory assertionFactory = mock(ClientAssertionFactory.class);
        when(assertionFactory.create()).thenReturn("assertion-jwt");
        client = new AuthGatewayClient(securityProperties, assertionFactory, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void loginPasswordSendsClientAssertionFormAndMapsToken() {
        server.createContext("/auth/login/password", exchange -> {
            capture(exchange);
            respond(exchange, 200, """
                    {"access_token":"at","refresh_token":"rt","token_type":"Bearer","expires_in":3600}
                    """, "application/json");
        });

        AuthGatewayClient.TokenResponse response = client.loginPassword("amy", "pwd");

        assertEquals("at", response.getAccessToken());
        assertEquals("rt", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(3600L, response.getExpiresIn());
        assertTrue(lastContentType.get().contains("application/x-www-form-urlencoded"));
        assertTrue(lastBody.get().contains("account=amy"));
        assertTrue(lastBody.get().contains("client_assertion=assertion-jwt"));
        assertTrue(lastBody.get().contains("target_aud=careermate-api"));
    }

    @Test
    void resetConfirmSendsJsonWithClientAssertion() {
        server.createContext("/auth/password/reset/confirm", exchange -> {
            capture(exchange);
            respond(exchange, 200, "{\"access_token\":\"new-at\"}", "application/json");
        });

        AuthGatewayClient.TokenResponse response = client.resetConfirm("ticket-1", "new-password");

        assertEquals("new-at", response.getAccessToken());
        assertTrue(lastContentType.get().contains("application/json"));
        assertTrue(lastBody.get().contains("\"reset_ticket\":\"ticket-1\""));
        assertTrue(lastBody.get().contains("\"client_assertion\":\"assertion-jwt\""));
    }

    @Test
    void tokenExchangeBuildsOauthTokenExchangeForm() {
        server.createContext("/oauth/token-exchange", exchange -> {
            capture(exchange);
            respond(exchange, 200, """
                    {"access_token":"exchanged","issued_token_type":"urn:ietf:params:oauth:token-type:access_token","token_type":"Bearer","expires_in":60,"scope":"rag:search"}
                    """, "application/json");
        });

        AuthGatewayClient.TokenExchangeResponse response = client.tokenExchange("subject", "ragforge-api", "rag:search");

        assertEquals("exchanged", response.getAccessToken());
        assertEquals("rag:search", response.getScope());
        assertTrue(lastBody.get().contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Atoken-exchange"));
        assertTrue(lastBody.get().contains("subject_token=subject"));
        assertTrue(lastBody.get().contains("requested_audience=ragforge-api"));
    }

    @Test
    void gatewayErrorMapsToFriendlyBizException() {
        server.createContext("/auth/sms/send", exchange ->
                respond(exchange, 429, "{\"error\":\"SMS_SEND_TOO_FREQUENT\",\"message\":\"gateway noisy\"}", "application/json"));

        BizException ex = org.junit.jupiter.api.Assertions.assertThrows(
                BizException.class,
                () -> client.sendSms("13800138000", "mobile_login"));

        assertEquals(429, ex.getCode());
        assertEquals("验证码已发送，请稍后再试", ex.getMessage());
    }

    @Test
    void unavailableGatewayReturnsInternalError() {
        server.createContext("/auth/token/refresh", exchange -> {
            exchange.close();
        });

        BizException ex = org.junit.jupiter.api.Assertions.assertThrows(
                BizException.class,
                () -> client.refresh("rt"));

        assertEquals(500, ex.getCode());
        assertEquals("认证服务不可用", ex.getMessage());
    }

    private void capture(HttpExchange exchange) throws IOException {
        lastContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
