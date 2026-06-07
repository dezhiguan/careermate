package com.careermate.ragforge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service("careermateRagForgeClient")
public class RagForgeClient {

    private final RagForgeProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RagForgeClient(RagForgeProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeoutMs());
        factory.setReadTimeout(properties.getTimeoutMs());
        this.restClient = RestClient.builder()
            .baseUrl(properties.getUrl())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("X-API-Key", properties.getApiKey())
            .requestFactory(factory)
            .build();
    }

    /**
     * 在 JD KB 中搜索；enabled=false 或 jdKbId 为空 → 返回空列表。
     * 任何异常 → log.warn + 返回空列表，不抛出。
     */
    public List<RagForgeChunk> searchJd(String query, int topK) {
        if (!properties.isEnabled()) {
            return List.of();
        }
        String jdKbIdRaw = properties.getJdKbId();
        if (jdKbIdRaw == null || jdKbIdRaw.isBlank()) {
            return List.of();
        }
        long kbId;
        try {
            kbId = Long.parseLong(jdKbIdRaw.trim());
        } catch (NumberFormatException e) {
            log.warn("ragforge.jdKbId 配置非数字，跳过 RAGForge 调用: {}", jdKbIdRaw);
            return List.of();
        }
        return search(kbId, query, topK, null);
    }

    /**
     * 在 Interview Q&A KB 中搜索；enabled=false 或 interviewKbId 为空 → 返回空列表。
     * 任何异常 → log.warn + 返回空列表，不抛出。
     */
    public List<RagForgeChunk> searchInterview(String query, int topK) {
        if (!properties.isEnabled()) {
            return List.of();
        }
        String kbIdRaw = properties.getInterviewKbId();
        if (kbIdRaw == null || kbIdRaw.isBlank()) {
            return List.of();
        }
        long kbId;
        try {
            kbId = Long.parseLong(kbIdRaw.trim());
        } catch (NumberFormatException e) {
            log.warn("ragforge.interviewKbId 配置非数字，跳过 RAGForge 调用: {}", kbIdRaw);
            return List.of();
        }
        return search(kbId, query, topK, null);
    }

    /**
     * 通用版本：在任意 KB 中搜索，可选 chunk_type 过滤。
     * enabled=false 直接返回空列表。任何异常 → 返回空列表。
     */
    public List<RagForgeChunk> search(Long kbId, String query, int topK, List<String> chunkTypes) {
        if (!properties.isEnabled() || kbId == null || query == null || query.isBlank()) {
            return List.of();
        }
        try {
            RagForgeSearchRequest.Filter filter = (chunkTypes == null || chunkTypes.isEmpty())
                ? null
                : new RagForgeSearchRequest.Filter(chunkTypes);

            RagForgeSearchRequest body = new RagForgeSearchRequest(
                query, List.of(kbId), "hybrid", topK, 3, 0.55, filter
            );

            String responseBody = restClient.post()
                .uri("/api/v1/search")
                .body(body)
                .retrieve()
                .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                return List.of();
            }

            JsonNode root = objectMapper.readTree(responseBody);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                log.warn("RAGForge 返回非 0 code: code={} body={}", code, responseBody);
                return List.of();
            }
            JsonNode results = root.path("data").path("results");
            if (!results.isArray() || results.isEmpty()) {
                return List.of();
            }
            List<RagForgeChunk> chunks = new ArrayList<>(results.size());
            for (JsonNode node : results) {
                chunks.add(new RagForgeChunk(
                    node.path("chunkId").isNumber() ? node.path("chunkId").asLong() : null,
                    node.path("docId").isNumber()   ? node.path("docId").asLong() : null,
                    node.path("filename").asText(null),
                    node.path("content").asText(""),
                    node.path("chunkType").asText(null),
                    node.path("finalScore").isNumber() ? node.path("finalScore").asDouble() : null
                ));
            }
            return chunks;
        } catch (Exception e) {
            log.warn("RAGForge search 失败（已降级）: query={} err={}", query, e.getMessage());
            return List.of();
        }
    }
}
