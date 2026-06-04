package com.careermate.llm.provider;

import com.careermate.common.exception.BizException;
import com.careermate.llm.LlmClient;
import com.careermate.llm.LlmProperties;
import com.careermate.llm.StreamCallback;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.llm.dto.ToolCallRequest;
import com.careermate.llm.dto.ToolCallResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String providerName;

    public OpenAiCompatibleLlmClient(
            LlmProperties llmProperties,
            ObjectMapper objectMapper,
            String providerName
    ) {
        this.llmProperties = llmProperties;
        this.objectMapper = objectMapper;
        this.providerName = providerName;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(llmProperties.getTimeoutMs()))
                .build();
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        long start = System.currentTimeMillis();
        validateConfig();

        String model = resolveModel(request);
        Double temperature = resolveTemperature(request);
        Integer maxTokens = resolveMaxTokens(request);
        String endpoint = normalizeEndpoint(llmProperties.getEndpoint()) + "/chat/completions";

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", buildMessages(request));
        payload.put("temperature", temperature);
        payload.put("max_tokens", maxTokens);
        payload.put("stream", false);

        try {
            String json = objectMapper.writeValueAsString(payload);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + llmProperties.getApiKey())
                    .timeout(Duration.ofMillis(llmProperties.getTimeoutMs()))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("LLM call failed: provider={}, model={}, status={}, latencyMs={}, body={}",
                        providerName, model, response.statusCode(), latency,
                        LlmProviderDefaults.sanitizeForLog(response.body()));
                throw new BizException(500, LlmProviderDefaults.userFacingHttpError(response.statusCode()));
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode firstChoice = root.path("choices").isArray() && root.path("choices").size() > 0
                    ? root.path("choices").get(0)
                    : null;
            String content = firstChoice == null ? "" : firstChoice.path("message").path("content").asText("");
            String finishReason = firstChoice == null ? "" : firstChoice.path("finish_reason").asText("");
            int inputTokens = root.path("usage").path("prompt_tokens").asInt(0);
            int outputTokens = root.path("usage").path("completion_tokens").asInt(0);

            return ChatResponse.builder()
                    .content(content)
                    .model(model)
                    .provider(providerName)
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .latencyMs(latency)
                    .finishReason(finishReason)
                    .build();
        } catch (BizException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            log.warn("LLM timeout: provider={}, model={}, timeoutMs={}",
                    providerName, model, llmProperties.getTimeoutMs());
            throw new BizException(504, "LLM 调用超时，请稍后重试");
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("LLM call exception: provider={}, model={}, message={}",
                    providerName, model, LlmProviderDefaults.sanitizeForLog(e.getMessage()), e);
            throw new BizException(500, "LLM 调用失败，请稍后重试");
        }
    }

    @Override
    public void streamChat(ChatRequest request, StreamCallback callback) {
        try {
            ChatResponse response = chat(request);
            callback.onToken(response.getContent() == null ? "" : response.getContent());
            callback.onComplete(response);
        } catch (Throwable t) {
            callback.onError(t);
        }
    }

    @Override
    public ToolCallResponse toolCall(ToolCallRequest request) {
        long start = System.currentTimeMillis();
        String model = request != null && request.getModel() != null && !request.getModel().isBlank()
                ? request.getModel()
                : llmProperties.getModel();

        return ToolCallResponse.builder()
                .content("当前为 toolCall 预留接口，尚未接入真实工具调用。")
                .toolCalls(Collections.emptyList())
                .model(model)
                .provider(providerName)
                .latencyMs(System.currentTimeMillis() - start)
                .build();
    }

    protected void validateConfig() {
        if (llmProperties.getApiKey() == null || llmProperties.getApiKey().isBlank()) {
            throw new BizException(400, "LLM API Key 未配置");
        }
        if (llmProperties.getEndpoint() == null || llmProperties.getEndpoint().isBlank()) {
            throw new BizException(400, "LLM Endpoint 未配置");
        }
    }

    private String resolveModel(ChatRequest request) {
        if (request != null && request.getModel() != null && !request.getModel().isBlank()) {
            return request.getModel();
        }
        return llmProperties.getModel();
    }

    private Double resolveTemperature(ChatRequest request) {
        if (request != null && request.getTemperature() != null) {
            return request.getTemperature();
        }
        return llmProperties.getTemperature();
    }

    private Integer resolveMaxTokens(ChatRequest request) {
        if (request != null && request.getMaxTokens() != null) {
            return request.getMaxTokens();
        }
        return llmProperties.getMaxTokens();
    }

    private List<Map<String, String>> buildMessages(ChatRequest request) {
        List<Map<String, String>> result = new ArrayList<>();
        if (request == null || request.getMessages() == null || request.getMessages().isEmpty()) {
            return result;
        }
        for (ChatMessage message : request.getMessages()) {
            if (message == null) {
                continue;
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("role", message.getRole());
            item.put("content", message.getContent());
            result.add(item);
        }
        return result;
    }

    private String normalizeEndpoint(String endpoint) {
        String value = endpoint == null ? "" : endpoint.trim();
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
