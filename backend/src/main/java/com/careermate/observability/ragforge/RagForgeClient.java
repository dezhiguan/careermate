package com.careermate.observability.ragforge;

import com.careermate.observability.RagForgeProperties;
import com.careermate.observability.TraceHeaderPropagator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lightweight RAGForge HTTP client with distributed trace propagation.
 * Business features should call this client rather than raw HTTP.
 */
@Slf4j
@Component
public class RagForgeClient {

    private final RagForgeProperties properties;
    private final TraceHeaderPropagator traceHeaderPropagator;
    private final Tracer tracer;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public RagForgeClient(
            RagForgeProperties properties,
            TraceHeaderPropagator traceHeaderPropagator,
            Tracer tracer,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.traceHeaderPropagator = traceHeaderPropagator;
        this.tracer = tracer;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build();
    }

    public RagForgeSearchResult search(RagForgeSearchRequest request) {
        if (!properties.isEnabled()) {
            return RagForgeSearchResult.disabled();
        }
        String kbId = request == null ? null : request.getKbId();
        int topK = request == null ? 0 : request.getTopK();
        String searchType = request == null ? null : request.getSearchType();
        return traced("ragforge.search", span -> {
            span.tag("ragforge.kb_id", safe(kbId));
            span.tag("ragforge.top_k", String.valueOf(topK));
            span.tag("ragforge.search_type", safe(searchType));
            long start = System.currentTimeMillis();
            try {
                String base = normalizeBaseUrl(properties.getUrl());
                String path = "/api/rag/search";
                Map<String, Object> body = Map.of(
                        "kbId", kbId == null ? "" : kbId,
                        "query", request.getQuery() == null ? "" : request.getQuery(),
                        "topK", topK,
                        "searchType", searchType == null ? "hybrid" : searchType
                );
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(base + path))
                        .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
                if (StringUtils.hasText(properties.getApiKey())) {
                    builder.header("Authorization", "Bearer " + properties.getApiKey());
                }
                traceHeaderPropagator.inject(builder);
                HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                long latencyMs = System.currentTimeMillis() - start;
                List<String> ids = parseDocumentIds(response.body());
                span.tag("ragforge.latency_ms", String.valueOf(latencyMs));
                span.tag("ragforge.result_count", String.valueOf(ids.size()));
                log.info("ragforge.search kbId={} topK={} searchType={} latencyMs={} resultCount={} status={}",
                        kbId, topK, searchType, latencyMs, ids.size(), response.statusCode());
                return RagForgeSearchResult.builder()
                        .success(response.statusCode() >= 200 && response.statusCode() < 300)
                        .latencyMs(latencyMs)
                        .resultCount(ids.size())
                        .documentIds(ids)
                        .build();
            } catch (Exception e) {
                long latencyMs = System.currentTimeMillis() - start;
                span.tag("ragforge.latency_ms", String.valueOf(latencyMs));
                span.tag("ragforge.result_count", "0");
                span.error(e);
                log.warn("ragforge.search failed kbId={} latencyMs={} error={}", kbId, latencyMs, e.getClass().getSimpleName());
                return RagForgeSearchResult.builder()
                        .success(false)
                        .latencyMs(latencyMs)
                        .resultCount(0)
                        .documentIds(List.of())
                        .build();
            }
        });
    }

    public boolean uploadDocument(String kbId, String documentId) {
        if (!properties.isEnabled()) {
            return false;
        }
        return tracedBoolean("ragforge.upload_document", span -> {
            span.tag("ragforge.kb_id", safe(kbId));
            long start = System.currentTimeMillis();
            try {
                String base = normalizeBaseUrl(properties.getUrl());
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(base + "/api/rag/documents/" + documentId + "/upload?kbId=" + safe(kbId)))
                        .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                        .POST(HttpRequest.BodyPublishers.noBody());
                traceHeaderPropagator.inject(builder);
                HttpResponse<Void> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
                long latencyMs = System.currentTimeMillis() - start;
                span.tag("ragforge.latency_ms", String.valueOf(latencyMs));
                return response.statusCode() >= 200 && response.statusCode() < 300;
            } catch (Exception e) {
                span.error(e);
                return false;
            }
        });
    }

    public boolean deleteDocument(String kbId, String documentId) {
        if (!properties.isEnabled()) {
            return false;
        }
        return tracedBoolean("ragforge.delete_document", span -> {
            span.tag("ragforge.kb_id", safe(kbId));
            long start = System.currentTimeMillis();
            try {
                String base = normalizeBaseUrl(properties.getUrl());
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(base + "/api/rag/documents/" + documentId + "?kbId=" + safe(kbId)))
                        .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                        .DELETE();
                traceHeaderPropagator.inject(builder);
                HttpResponse<Void> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
                long latencyMs = System.currentTimeMillis() - start;
                span.tag("ragforge.latency_ms", String.valueOf(latencyMs));
                return response.statusCode() >= 200 && response.statusCode() < 300;
            } catch (Exception e) {
                span.error(e);
                return false;
            }
        });
    }

    private <T> T traced(String spanName, SpanWork<T> work) {
        Span span = tracer.nextSpan().name(spanName);
        try (Tracer.SpanInScope scope = tracer.withSpan(span.start())) {
            try {
                return work.run(span);
            } catch (Exception e) {
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new IllegalStateException(e);
            }
        } catch (RuntimeException | Error e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    private boolean tracedBoolean(String spanName, SpanWork<Boolean> work) {
        return traced(spanName, work);
    }

    private List<String> parseDocumentIds(String body) {
        if (!StringUtils.hasText(body)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode items = root.path("data").path("items");
            if (!items.isArray()) {
                return List.of();
            }
            List<String> ids = new ArrayList<>();
            for (JsonNode item : items) {
                String id = item.path("documentId").asText(null);
                if (StringUtils.hasText(id)) {
                    ids.add(id);
                }
            }
            return ids;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String normalizeBaseUrl(String url) {
        String value = url == null ? "" : url.trim();
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    @FunctionalInterface
    private interface SpanWork<T> {
        T run(Span span) throws Exception;
    }
}
