package com.careermate.agent.memory.ltm;

import com.careermate.llm.LlmProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * A4：文本向量化（DashScope text-embedding-v3，OpenAI 兼容 endpoint）。
 * Redis 缓存（key=sha256(model+text)，TTL 可配）。禁用/无 key/异常一律返回空，静默降级，不影响对话。
 */
@Slf4j
@Component
public class EmbeddingClient {

    private final LtmProperties properties;
    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final HttpClient httpClient;

    public EmbeddingClient(LtmProperties properties, LlmProperties llmProperties, ObjectMapper objectMapper,
                           ObjectProvider<StringRedisTemplate> redisProvider) {
        this.properties = properties;
        this.llmProperties = llmProperties;
        this.objectMapper = objectMapper;
        this.redisProvider = redisProvider;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(500, properties.getTimeoutMs())))
                .build();
    }

    public Optional<float[]> embed(String text) {
        if (!properties.isEnabled() || !StringUtils.hasText(text)) {
            return Optional.empty();
        }
        String apiKey = resolveApiKey();
        if (!StringUtils.hasText(apiKey)) {
            return Optional.empty();
        }
        String cacheKey = "ltm:emb:" + sha256(properties.getEmbeddingModel() + ':' + text);
        Optional<float[]> cached = readCache(cacheKey);
        if (cached.isPresent()) {
            return cached;
        }
        try {
            float[] vec = callEmbedding(text, apiKey);
            if (vec != null && vec.length > 0) {
                writeCache(cacheKey, vec);
                return Optional.of(vec);
            }
        } catch (Exception e) {
            log.warn("embedding 调用失败（长期记忆静默降级）: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private String resolveApiKey() {
        return StringUtils.hasText(properties.getApiKey())
                ? properties.getApiKey() : llmProperties.getApiKey();
    }

    private float[] callEmbedding(String text, String apiKey) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "model", properties.getEmbeddingModel(),
                "input", text,
                "dimensions", properties.getDimensions()));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(properties.getEmbeddingEndpoint()))
                .timeout(Duration.ofMillis(Math.max(500, properties.getTimeoutMs())))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) {
            log.warn("embedding HTTP {}（静默降级）", resp.statusCode());
            return null;
        }
        JsonNode data = objectMapper.readTree(resp.body()).path("data");
        if (!data.isArray() || data.isEmpty()) {
            return null;
        }
        JsonNode arr = data.get(0).path("embedding");
        if (!arr.isArray()) {
            return null;
        }
        float[] vec = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            vec[i] = (float) arr.get(i).asDouble();
        }
        return vec;
    }

    private Optional<float[]> readCache(String key) {
        try {
            StringRedisTemplate redis = redisProvider.getIfAvailable();
            if (redis == null) {
                return Optional.empty();
            }
            String v = redis.opsForValue().get(key);
            if (!StringUtils.hasText(v)) {
                return Optional.empty();
            }
            String[] parts = v.split(",");
            float[] vec = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                vec[i] = Float.parseFloat(parts[i]);
            }
            return Optional.of(vec);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private void writeCache(String key, float[] vec) {
        try {
            StringRedisTemplate redis = redisProvider.getIfAvailable();
            if (redis == null) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < vec.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(vec[i]);
            }
            redis.opsForValue().set(key, sb.toString(), Duration.ofSeconds(properties.getCacheTtlSeconds()));
        } catch (Exception ignored) {
            // 缓存写失败不影响
        }
    }

    private String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
