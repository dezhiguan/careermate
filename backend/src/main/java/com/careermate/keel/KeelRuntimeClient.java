package com.careermate.keel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Private, authenticated facade to the Python SDK. No vendor credentials live here. */
@Component
@ConditionalOnProperty(name = "careermate.keel.enabled", havingValue = "true")
public class KeelRuntimeClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json;
    private final String endpoint;
    private final String token;

    @org.springframework.beans.factory.annotation.Autowired
    public void verifyGovernedConfiguration(
            @Value("${careermate.llm.provider}") String provider,
            @Value("${careermate.agent.framework.enabled:false}") boolean framework,
            @Value("${careermate.agent.secondary-llm.enabled:false}") boolean secondary) {
        if (!"keel".equals(provider) || framework || secondary) {
            throw new IllegalStateException("Managed CareerMate requires provider=keel and disables direct framework/secondary model clients");
        }
    }

    public KeelRuntimeClient(ObjectMapper json, @Value("${CAREERMATE_KEEL_URL}") String endpoint,
                             @Value("${CAREERMATE_SERVICE_TOKEN}") String token) {
        if (endpoint == null || endpoint.isBlank() || token == null || token.isBlank()) {
            throw new IllegalStateException("CareerMate Keel endpoint and service credential are required");
        }
        this.json = json;
        this.endpoint = endpoint.replaceAll("/+$", "");
        this.token = token;
    }

    private HttpRequest request(String operation, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(endpoint + "/internal/" + operation))
                .timeout(Duration.ofSeconds(120)).header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token);
        var trace = MDC.get("traceId");
        if (trace != null && trace.matches("[0-9a-f]{32}")) {
            var span = MDC.get("spanId");
            if (span == null || !span.matches("[0-9a-f]{16}")) {
                span = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            }
            builder.header("traceparent", "00-" + trace + "-" + span + "-01");
        }
        return builder.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
    }

    public JsonNode post(String operation, Object body) {
        try {
            var response = http.send(request(operation, body), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("Keel " + operation + " rejected: HTTP " + response.statusCode());
            }
            return json.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Keel request interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("Keel " + operation + " unavailable", e);
        }
    }

    public void stream(Object body, Consumer<JsonNode> event) {
        try {
            var response = http.send(request("llm", body), HttpResponse.BodyHandlers.ofInputStream());
            try (var input = response.body()) {
                if (response.statusCode() >= 300) {
                    throw new IllegalStateException("Keel stream rejected: HTTP " + response.statusCode());
                }
                try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                    String line;
                    boolean completed = false;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank()) continue;
                        var node = json.readTree(line);
                        if (node.has("error")) throw new IllegalStateException("Keel generation failed");
                        event.accept(node);
                        if (node.has("final")) completed = true;
                    }
                    if (!completed) throw new IllegalStateException("Keel stream ended without final");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Keel stream interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("Keel stream unavailable", e);
        }
    }
}
