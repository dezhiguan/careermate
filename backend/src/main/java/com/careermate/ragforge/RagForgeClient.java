package com.careermate.ragforge;

import com.careermate.auth.gateway.AuthGatewayClient;
import com.careermate.observability.MdcKeys;
import com.careermate.observability.TraceHeaderPropagator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.client.RestTemplate;

/**
 * RAGForge HTTP client. Uses {@link RestTemplate} so SkyWalking Java Agent
 * ({@code apm-resttemplate-6.x-plugin}) can inject {@code sw8} on outbound calls.
 * Business headers (traceparent, X-Request-Id) are added via {@link TraceHeaderPropagator}.
 */
@Slf4j
@Service("careermateRagForgeClient")
public class RagForgeClient {

    private final RagForgeProperties properties;
    private final TraceHeaderPropagator traceHeaderPropagator;
    private final AuthGatewayClient authGatewayClient;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, CachedToken> exchangedTokens = new ConcurrentHashMap<>();

    @Autowired
    public RagForgeClient(RagForgeProperties properties, TraceHeaderPropagator traceHeaderPropagator, AuthGatewayClient authGatewayClient) {
        this(properties, traceHeaderPropagator, authGatewayClient, null);
    }

    /** For unit tests without trace propagation. */
    public RagForgeClient(RagForgeProperties properties) {
        this(properties, null, null, null);
    }

    /** Backward-compatible test constructor with custom {@link RestTemplate}. */
    public RagForgeClient(
            RagForgeProperties properties,
            TraceHeaderPropagator traceHeaderPropagator,
            RestTemplate restTemplateOverride
    ) {
        this(properties, traceHeaderPropagator, null, restTemplateOverride);
    }

    /** For tests that supply a custom {@link RestTemplate} (e.g. sw8 simulation). */
    public RagForgeClient(
            RagForgeProperties properties,
            TraceHeaderPropagator traceHeaderPropagator,
            AuthGatewayClient authGatewayClient,
            RestTemplate restTemplateOverride
    ) {
        this.properties = properties;
        this.traceHeaderPropagator = traceHeaderPropagator;
        this.authGatewayClient = authGatewayClient;
        if (restTemplateOverride != null) {
            this.restTemplate = restTemplateOverride;
        } else {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(properties.getTimeoutMs());
            factory.setReadTimeout(properties.getTimeoutMs());
            RestTemplate template = new RestTemplate(factory);
            if (traceHeaderPropagator != null) {
                template.getInterceptors().add(traceHeaderPropagator.clientHttpRequestInterceptor());
            }
            template.getInterceptors().add(this::logOutboundTraceHeaders);
            this.restTemplate = template;
        }
    }

    public RagForgeProperties getProperties() {
        return properties;
    }

    /** RAGForge Result 成功码为 200；与 CareerMate ApiResponse(0) 不同，需兼容两者。 */
    private static boolean isSuccessCode(int code) {
        return code == 0 || code == 200;
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, Object body, Object... uriVars) {
        HttpHeaders headers = defaultHeaders();
        HttpEntity<Object> entity = body == null ? new HttpEntity<>(headers) : new HttpEntity<>(body, headers);
        String url = properties.getUrl() + path;
        log.info("ragforge.http.before {} {}", method, path);
        return restTemplate.exchange(url, method, entity, String.class, uriVars);
    }

    private HttpHeaders defaultHeaders() {
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /** 仅鉴权头（X-API-Key 或交换后的 Bearer），不设 Content-Type，供 multipart 复用。 */
    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (StringUtils.hasText(properties.getApiKey())) {
            headers.set("X-API-Key", properties.getApiKey());
        } else {
            String exchangedToken = resolveExchangedToken();
            if (StringUtils.hasText(exchangedToken)) {
                headers.setBearerAuth(exchangedToken);
            }
        }
        return headers;
    }

    private String resolveExchangedToken() {
        String subjectToken = currentBearerToken();
        if (!StringUtils.hasText(subjectToken) || authGatewayClient == null) {
            return null;
        }
        String cacheKey = sha256(subjectToken + "|" + properties.getRequestedAudience() + "|" + properties.getRequestedScopes());
        CachedToken cached = exchangedTokens.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtEpochMs() > now) {
            return cached.token();
        }
        AuthGatewayClient.TokenExchangeResponse response = authGatewayClient.tokenExchange(
                subjectToken,
                properties.getRequestedAudience(),
                properties.getRequestedScopes());
        if (response == null || !StringUtils.hasText(response.getAccessToken())) {
            return null;
        }
        long ttlMs = Math.max(1000L, Math.min(properties.getExchangedTokenCacheTtlMs(), response.getExpiresIn() * 1000L));
        exchangedTokens.put(cacheKey, new CachedToken(response.getAccessToken(), now + ttlMs));
        return response.getAccessToken();
    }

    private String currentBearerToken() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return null;
        }
        String authorization = servletRequestAttributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring("Bearer ".length()).trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private record CachedToken(String token, long expiresAtEpochMs) {
    }

    private org.springframework.http.client.ClientHttpResponse logOutboundTraceHeaders(
            org.springframework.http.HttpRequest request,
            byte[] body,
            org.springframework.http.client.ClientHttpRequestExecution execution
    ) throws java.io.IOException {
        org.springframework.http.client.ClientHttpResponse response = execution.execute(request, body);
        String sw8 = request.getHeaders().getFirst("sw8");
        String traceparent = request.getHeaders().getFirst("traceparent");
        String requestId = request.getHeaders().getFirst(MdcKeys.HEADER_REQUEST_ID);
        String careerMateTraceId = traceHeaderPropagator != null ? traceHeaderPropagator.currentTraceId() : null;
        String ragTraceId = response.getHeaders().getFirst(MdcKeys.HEADER_TRACE_ID);
        log.info(
                "ragforge.http.after {} {} sw8Present={} traceparentPresent={} requestId={} careerMateTraceId={} ragTraceId={}",
                request.getMethod(),
                request.getURI().getPath(),
                StringUtils.hasText(sw8),
                StringUtils.hasText(traceparent),
                requestId,
                careerMateTraceId,
                ragTraceId
        );
        return response;
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
                ResponseEntity<String> response = exchange(
                        "/api/v1/documents/{id}/chunks?page={page}&size={size}",
                        HttpMethod.GET,
                        null,
                        docId,
                        page,
                        size
                );
                String responseBody = response.getBody();
                if (responseBody == null || responseBody.isBlank()) {
                    break;
                }
                JsonNode root = objectMapper.readTree(responseBody);
                if (!isSuccessCode(root.path("code").asInt(-1))) {
                    log.warn("RAGForge fetchDocumentChunks 返回失败: docId={} code={}", docId, root.path("code").asInt(-1));
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
     * 在 JD 库中按 docId 精确取该 JD 的全部 chunks（走 /search + docIds 过滤，API-Key 可用）。
     * enabled=false / jdKbId 未配 / docId 非法 → 空列表。
     */
    public List<RagForgeChunk> searchJdByDocId(Long docId, int topK) {
        if (!properties.isEnabled() || docId == null || docId <= 0) {
            return List.of();
        }
        String jdKbIdRaw = properties.getJdKbId();
        if (jdKbIdRaw == null || jdKbIdRaw.isBlank()) {
            return List.of();
        }
        try {
            return searchByDocId(Long.parseLong(jdKbIdRaw.trim()), docId, topK);
        } catch (NumberFormatException e) {
            log.warn("ragforge.jdKbId 配置非数字，跳过 RAGForge 调用: {}", jdKbIdRaw);
            return List.of();
        }
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
            if (chunkType != null) {
                body.put("chunkType", chunkType);
            }

            ResponseEntity<String> response = exchange("/api/v1/documents/text", HttpMethod.POST, body);
            String responseBody = response.getBody();
            if (responseBody == null) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(responseBody);
            if (!isSuccessCode(root.path("code").asInt(-1))) {
                log.warn("RAGForge syncText 返回失败 code={}", root.path("code").asInt(-1));
                return Optional.empty();
            }
            long docId = root.path("data").path("docId").asLong(-1);
            if (docId <= 0) {
                return Optional.empty();
            }
            return Optional.of(docId);
        } catch (Exception e) {
            log.warn("RAGForge syncText 失败（已降级）: title={} err={}", title, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 通过 relay 入库端点（{@code POST /api/v1/documents}，multipart）同步入库一段文本，成功返回 docId。
     * 这是 API-Key 可达的通用入库通道（采集器同款）：{@code file}=正文，{@code meta}=IngestCommand JSON。
     * externalId 用于幂等（同 kb 同 externalId 重复入库→409，返回既有 docId）。
     * enabled=false / kbId 空 / 内容空 → Optional.empty()；任何异常 → warn + empty。
     */
    public Optional<Long> ingestText(Long kbId, String externalId, String content, String chunkType) {
        if (!properties.isEnabled() || kbId == null || content == null || content.isBlank()) {
            return Optional.empty();
        }
        try {
            String filename = "ltm-" + (externalId == null ? "doc" : externalId) + ".txt";
            var meta = new java.util.HashMap<String, Object>();
            meta.put("kbId", kbId);
            meta.put("filename", filename);
            meta.put("contentType", "text/plain");
            if (chunkType != null) {
                meta.put("chunkType", chunkType);
            }
            if (StringUtils.hasText(externalId)) {
                meta.put("identity", java.util.Map.of("externalId", externalId));
            }

            org.springframework.core.io.ByteArrayResource fileResource =
                    new org.springframework.core.io.ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
                        @Override
                        public String getFilename() {
                            return filename;
                        }
                    };
            org.springframework.util.MultiValueMap<String, Object> body =
                    new org.springframework.util.LinkedMultiValueMap<>();
            body.add("file", fileResource);
            body.add("meta", objectMapper.writeValueAsString(meta));

            HttpHeaders headers = authHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            ResponseEntity<String> response = restTemplate.exchange(
                    properties.getUrl() + "/api/v1/documents", HttpMethod.POST,
                    new HttpEntity<>(body, headers), String.class);

            String responseBody = response.getBody();
            if (responseBody == null || responseBody.isBlank()) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(responseBody);
            // relay 直接返回 {documentId,status}，无 code/msg 包裹；冲突时返回 {error,existingDocId}。
            long docId = root.path("documentId").asLong(-1);
            if (docId <= 0) {
                docId = root.path("existingDocId").asLong(-1);
            }
            if (docId <= 0) {
                log.warn("RAGForge ingestText 未拿到 docId: kbId={} body={}", kbId, responseBody);
                return Optional.empty();
            }
            return Optional.of(docId);
        } catch (Exception e) {
            log.warn("RAGForge ingestText 失败（已降级）: kbId={} err={}", kbId, e.getMessage());
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
            exchange("/api/v1/documents/{id}", HttpMethod.DELETE, null, docId);
        } catch (Exception e) {
            log.warn("RAGForge deleteDocument 失败（忽略）: docId={} err={}", docId, e.getMessage());
        }
    }

    /**
     * 按 docId 精确取某文档的 chunks（走 API-Key 开放的 /search + docIds 过滤，
     * 替代不对 API-Key 开放的 /documents/{id}/chunks）。enabled=false 或参数非法 → 空列表。
     */
    public List<RagForgeChunk> searchByDocId(Long kbId, Long docId, int topK) {
        if (!properties.isEnabled() || kbId == null || docId == null || docId <= 0) {
            return List.of();
        }
        return doSearch("岗位职责", List.of(kbId), List.of(docId), topK, null);
    }

    /**
     * 通用版本：在任意 KB 中搜索，可选 chunk_type 过滤。
     * enabled=false 直接返回空列表。任何异常 → 返回空列表。
     */
    public List<RagForgeChunk> search(Long kbId, String query, int topK, List<String> chunkTypes) {
        if (!properties.isEnabled() || kbId == null || query == null || query.isBlank()) {
            return List.of();
        }
        return doSearch(query, List.of(kbId), null, topK, chunkTypes);
    }

    /**
     * 在指定 KB 中按一组 docId 收窄搜索（长期记忆按 userId↔docId 隔离用）。
     * docIds 为空 → 返回空列表（避免误召回全库）。enabled=false / 参数非法 → 空列表。
     */
    public List<RagForgeChunk> searchInKb(Long kbId, String query, int topK,
                                          List<Long> docIds, List<String> chunkTypes) {
        if (!properties.isEnabled() || kbId == null || query == null || query.isBlank()) {
            return List.of();
        }
        if (docIds == null || docIds.isEmpty()) {
            return List.of();
        }
        return doSearch(query, List.of(kbId), docIds, topK, chunkTypes);
    }

    private List<RagForgeChunk> doSearch(String query, List<Long> kbIds, List<Long> docIds,
                                         int topK, List<String> chunkTypes) {
        try {
            RagForgeSearchRequest.Filter filter = (chunkTypes == null || chunkTypes.isEmpty())
                ? null
                : new RagForgeSearchRequest.Filter(chunkTypes);

            RagForgeSearchRequest body = new RagForgeSearchRequest(
                query, kbIds, docIds, "hybrid", topK, 3, 0.55, filter
            );
            Long kbId = kbIds == null || kbIds.isEmpty() ? null : kbIds.get(0);

            log.info(
                    "ragforge.search.before kbId={} topK={} queryLen={}",
                    kbId,
                    topK,
                    query.length()
            );

            ResponseEntity<String> response = exchange("/api/v1/search", HttpMethod.POST, body);
            String responseBody = response.getBody();
            if (responseBody == null || responseBody.isBlank()) {
                return List.of();
            }

            JsonNode root = objectMapper.readTree(responseBody);
            int code = root.path("code").asInt(-1);
            if (!isSuccessCode(code)) {
                log.warn("RAGForge search 返回失败 code={}", code);
                return List.of();
            }
            JsonNode results = root.path("data").path("results");
            if (!results.isArray() || results.isEmpty()) {
                log.info("ragforge.search.after kbId={} resultCount=0", kbId);
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
                    node.path("finalScore").isNumber() ? node.path("finalScore").asDouble() : null,
                    node.path("vectorScore").isNumber() ? node.path("vectorScore").asDouble() : null
                ));
            }
            log.info("ragforge.search.after kbId={} resultCount={}", kbId, chunks.size());
            return chunks;
        } catch (Exception e) {
            log.warn("RAGForge search 失败（已降级）: query={} err={}", query, e.getMessage());
            return List.of();
        }
    }
}
