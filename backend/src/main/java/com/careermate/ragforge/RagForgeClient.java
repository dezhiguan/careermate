package com.careermate.ragforge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    public RagForgeProperties getProperties() {
        return properties;
    }

    /** RAGForge Result 成功码为 200；与 CareerMate ApiResponse(0) 不同，需兼容两者。 */
    private static boolean isSuccessCode(int code) {
        return code == 0 || code == 200;
    }

    private Long parsePersonalKbId() {
        String raw = properties.getPersonalKbId();
        if (raw == null || raw.isBlank()) return null;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("ragforge.personalKbId 配置非数字: {}", raw);
            return null;
        }
    }

    /**
     * 按 docId 直接拉取文档分块（比 hybrid search 更可靠，用于按 JD 生成简历）。
     * enabled=false → 返回空列表；任何异常 → log.warn + 返回空列表。
     */
    public List<RagForgeChunk> fetchDocumentChunks(Long docId) {
        if (!properties.isEnabled() || docId == null || docId <= 0) {
            return List.of();
        }
        try {
            List<RagForgeChunk> all = new ArrayList<>();
            int page = 1;
            int size = 100;
            while (true) {
                String responseBody = restClient.get()
                        .uri("/api/v1/documents/{id}/chunks?page={page}&size={size}", docId, page, size)
                        .retrieve()
                        .body(String.class);
                if (responseBody == null || responseBody.isBlank()) {
                    break;
                }
                JsonNode root = objectMapper.readTree(responseBody);
                if (!isSuccessCode(root.path("code").asInt(-1))) {
                    log.warn("RAGForge fetchDocumentChunks 返回失败: docId={} body={}", docId, responseBody);
                    break;
                }
                JsonNode list = root.path("data").path("list");
                if (!list.isArray() || list.isEmpty()) {
                    break;
                }
                for (JsonNode node : list) {
                    all.add(new RagForgeChunk(
                            node.path("chunkIndex").isNumber() ? node.path("chunkIndex").asLong() : null,
                            docId,
                            null,
                            node.path("content").asText(""),
                            null,
                            null
                    ));
                }
                long total = root.path("data").path("total").asLong(all.size());
                if ((long) page * size >= total) {
                    break;
                }
                page++;
            }
            return all;
        } catch (Exception e) {
            log.warn("RAGForge fetchDocumentChunks 失败（已降级）: docId={} err={}", docId, e.getMessage());
            return List.of();
        }
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
     * 把纯文本同步到指定 KB，成功返回 RAGForge 的 docId，失败返回 Optional.empty()。
     * enabled=false 或 personalKbId 为空 → 直接返回 empty。
     */
    public Optional<Long> syncText(Long kbId, String title, String content, String chunkType) {
        if (!properties.isEnabled() || kbId == null) {
            return Optional.empty();
        }
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        try {
            var body = new java.util.HashMap<String, Object>();
            body.put("kbId", kbId);
            body.put("title", title == null ? "简历" : title);
            body.put("content", content);
            if (chunkType != null) body.put("chunkType", chunkType);

            String responseBody = restClient.post()
                .uri("/api/v1/documents/text")
                .body(body)
                .retrieve()
                .body(String.class);

            if (responseBody == null) return Optional.empty();
            JsonNode root = objectMapper.readTree(responseBody);
            if (!isSuccessCode(root.path("code").asInt(-1))) {
                log.warn("RAGForge syncText 返回失败 code: {}", responseBody);
                return Optional.empty();
            }
            long docId = root.path("data").path("docId").asLong(-1);
            if (docId <= 0) return Optional.empty();
            return Optional.of(docId);
        } catch (Exception e) {
            log.warn("RAGForge syncText 失败（已降级）: title={} err={}", title, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 删除 RAGForge 中的文档。失败只 warn，不抛异常。
     */
    public void deleteDocument(Long docId) {
        if (!properties.isEnabled() || docId == null) {
            return;
        }
        try {
            restClient.delete()
                .uri("/api/v1/documents/{id}", docId)
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            log.warn("RAGForge deleteDocument 失败（忽略）: docId={} err={}", docId, e.getMessage());
        }
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
            if (!isSuccessCode(code)) {
                log.warn("RAGForge 返回失败 code: code={} body={}", code, responseBody);
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
