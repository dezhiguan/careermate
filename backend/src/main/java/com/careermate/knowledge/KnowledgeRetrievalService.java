package com.careermate.knowledge;

import com.careermate.agent.tool.rag.RagRetrieveRequest;
import com.careermate.agent.tool.rag.RagRetrieveResult;
import com.careermate.agent.tool.rag.RagRetrieveScene;
import com.careermate.agent.tool.rag.RagRetrievedChunk;
import com.careermate.agent.tool.rag.RagRetrieverChunkType;
import com.careermate.ragforge.RagForgeChunk;
import com.careermate.ragforge.RagForgeClient;
import com.careermate.market.dto.MarketSourceCitationVO;
import com.careermate.ragforge.RagForgeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 统一 RAG 检索适配层。所有业务侧知识检索应优先经本服务路由至 {@link RagForgeClient}。
 *
 * <p>T10 迁移状态 — 仍直连 {@code RagForgeClient.searchJd} 的路径（待后续任务收敛）：
 * <ul>
 *   <li>{@code GenerateResumeWorkflowRunner} — 简历生成 workflow 内 JD 参考检索</li>
 * </ul>
 * 已迁移：agent rag_retriever、market、interview KB、jobmatch 分析、opportunity 列表/详情 fallback 检索。
 */
@Slf4j
@Service
public class KnowledgeRetrievalService {

    public static final String ERROR_RAGFORGE_DISABLED = "RAGFORGE_DISABLED";
    public static final String ERROR_KB_NOT_CONFIGURED = "KB_NOT_CONFIGURED";
    public static final String ERROR_EMPTY_RESULTS = "EMPTY_RESULTS";
    public static final String ERROR_QUERY_MISSING = "QUERY_MISSING";
    public static final String ERROR_RAGFORGE_FAILED = "RAGFORGE_FAILED";

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 50;

    private final RagForgeClient ragForgeClient;
    private final RagForgeProperties ragForgeProperties;

    public KnowledgeRetrievalService(RagForgeClient ragForgeClient, RagForgeProperties ragForgeProperties) {
        this.ragForgeClient = ragForgeClient;
        this.ragForgeProperties = ragForgeProperties;
    }

    public RagRetrieveResult retrieve(RagRetrieveRequest request) {
        long start = System.currentTimeMillis();
        String query = request.getQuery() == null ? "" : request.getQuery().trim();
        RagRetrieveScene scene = request.getScene() == null ? RagRetrieveScene.GENERAL : request.getScene();
        int topK = normalizeTopK(request.getTopK());

        if (!StringUtils.hasText(query)) {
            return RagRetrieveResult.fallback(query, scene, ERROR_QUERY_MISSING, elapsed(start));
        }
        if (!ragForgeProperties.isEnabled()) {
            return RagRetrieveResult.fallback(query, scene, ERROR_RAGFORGE_DISABLED, elapsed(start));
        }
        Optional<String> configError = validateKbConfig(scene);
        if (configError.isPresent()) {
            return RagRetrieveResult.fallback(query, scene, configError.get(), elapsed(start));
        }

        try {
            List<RagForgeChunk> rawChunks = searchByScene(scene, query, topK, request.chunkTypeFilters(), request.docIdFilters());
            if (rawChunks == null || rawChunks.isEmpty()) {
                return RagRetrieveResult.fallback(query, scene, ERROR_EMPTY_RESULTS, elapsed(start));
            }
            List<RagRetrievedChunk> chunks = mapChunks(rawChunks, scene);
            return RagRetrieveResult.builder()
                    .success(true)
                    .query(query)
                    .scene(scene)
                    .chunks(chunks)
                    .fallbackUsed(false)
                    .errorCode(null)
                    .latencyMs(elapsed(start))
                    .build();
        } catch (Exception e) {
            log.warn("Knowledge retrieval failed: scene={} queryLen={} err={}", scene, query.length(), e.getMessage());
            return RagRetrieveResult.fallback(query, scene, ERROR_RAGFORGE_FAILED, elapsed(start));
        }
    }

    public String retrieveContextText(RagRetrieveScene scene, String query, int topK) {
        RagRetrieveResult result = retrieve(RagRetrieveRequest.builder()
                .query(query)
                .scene(scene)
                .topK(topK)
                .build());
        if (!result.isSuccess()) {
            return "";
        }
        return KnowledgeRetrievalSupport.joinChunkContents(
                result.getChunks(),
                KnowledgeRetrievalSupport.DEFAULT_CONTEXT_CHARS
        );
    }

    public String retrieveMergedContextText(List<RagRetrieveRequest> requests, int maxChars) {
        RagRetrieveResult merged = retrieveMerged(requests);
        if (!merged.isSuccess()) {
            return "";
        }
        return KnowledgeRetrievalSupport.joinChunkContents(merged.getChunks(), maxChars);
    }

    public RagRetrieveResult retrieveMerged(List<RagRetrieveRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return RagRetrieveResult.fallback(null, RagRetrieveScene.GENERAL, ERROR_QUERY_MISSING, 0);
        }
        Map<String, RagRetrievedChunk> distinct = new LinkedHashMap<>();
        String firstQuery = null;
        RagRetrieveScene scene = requests.get(0).getScene() == null
                ? RagRetrieveScene.GENERAL
                : requests.get(0).getScene();
        long totalLatency = 0;
        boolean anySuccess = false;
        for (RagRetrieveRequest request : requests) {
            RagRetrieveResult result = retrieve(request);
            totalLatency += result.getLatencyMs();
            if (firstQuery == null) {
                firstQuery = result.getQuery();
            }
            if (result.isSuccess()) {
                anySuccess = true;
                appendDistinct(distinct, result.getChunks());
            }
        }
        if (!anySuccess) {
            return RagRetrieveResult.fallback(firstQuery, scene, ERROR_EMPTY_RESULTS, totalLatency);
        }
        List<RagRetrievedChunk> chunks = new ArrayList<>(distinct.values());
        return RagRetrieveResult.builder()
                .success(true)
                .query(firstQuery)
                .scene(scene)
                .chunks(chunks)
                .fallbackUsed(false)
                .errorCode(null)
                .latencyMs(totalLatency)
                .build();
    }

    public List<MarketSourceCitationVO> toMarketCitations(RagRetrieveResult result) {
        if (result == null || !result.isSuccess() || result.getChunks() == null) {
            return List.of();
        }
        return result.getChunks().stream().map(this::toMarketCitation).toList();
    }

    public List<String> toSourceSummaries(RagRetrieveResult result) {
        if (result == null || !result.isSuccess() || result.getChunks() == null) {
            return List.of();
        }
        return result.getChunks().stream()
                .map(chunk -> "[" + chunk.getCitation() + "] " + chunk.getContentPreview())
                .toList();
    }

    private MarketSourceCitationVO toMarketCitation(RagRetrievedChunk chunk) {
        MarketSourceCitationVO citation = new MarketSourceCitationVO();
        citation.setCitation(chunk.getCitation());
        citation.setContentPreview(chunk.getContentPreview());
        citation.setSourceTitle(chunk.getSourceTitle());
        citation.setScore(chunk.getScore());
        citation.setChunkType(chunk.getChunkType() == null ? null : chunk.getChunkType().name());
        return citation;
    }

    private List<RagForgeChunk> searchByScene(
            RagRetrieveScene scene,
            String query,
            int topK,
            List<String> chunkTypes,
            List<Long> docIds
    ) {
        // 按 docId 精确检索：走 /search + docIds 过滤（API-Key 开放），绕开对 API-Key 不开放的 /documents/{id}/chunks
        if (docIds != null && !docIds.isEmpty() && scene == RagRetrieveScene.OPPORTUNITY) {
            return ragForgeClient.searchJdByDocId(docIds.get(0), topK);
        }
        return switch (scene) {
            case INTERVIEW -> ragForgeClient.searchInterview(query, topK);
            case OPPORTUNITY -> ragForgeClient.searchJd(query, topK);
            // A1-4：精确 scene→kbId（薪资行情库/目标公司库），未配置时回退 JD 库保持兼容
            case MARKET -> searchKbIdOrFallbackJd(ragForgeProperties.getMarketKbId(), query, topK, chunkTypes);
            case COMPANY -> searchKbIdOrFallbackJd(ragForgeProperties.getCompanyKbId(), query, topK, chunkTypes);
            case RESUME -> searchKbId(ragForgeProperties.getPersonalKbId(), query, topK, chunkTypes);
            case GENERAL -> searchGeneral(query, topK, chunkTypes);
        };
    }

    private List<RagForgeChunk> searchKbIdOrFallbackJd(String kbIdRaw, String query, int topK, List<String> chunkTypes) {
        Long kbId = parseKbId(kbIdRaw);
        if (kbId == null) {
            return ragForgeClient.searchJd(query, topK);
        }
        return ragForgeClient.search(kbId, query, topK, chunkTypes);
    }

    private List<RagForgeChunk> searchGeneral(String query, int topK, List<String> chunkTypes) {
        List<RagForgeChunk> jdChunks = ragForgeClient.searchJd(query, topK);
        if (!jdChunks.isEmpty()) {
            return jdChunks;
        }
        return ragForgeClient.searchInterview(query, topK);
    }

    private List<RagForgeChunk> searchKbId(String kbIdRaw, String query, int topK, List<String> chunkTypes) {
        Long kbId = parseKbId(kbIdRaw);
        if (kbId == null) {
            return List.of();
        }
        return ragForgeClient.search(kbId, query, topK, chunkTypes);
    }

    private Optional<String> validateKbConfig(RagRetrieveScene scene) {
        return switch (scene) {
            case INTERVIEW -> kbConfigured(ragForgeProperties.getInterviewKbId())
                    ? Optional.empty()
                    : Optional.of(ERROR_KB_NOT_CONFIGURED);
            case OPPORTUNITY -> kbConfigured(ragForgeProperties.getJdKbId())
                    ? Optional.empty()
                    : Optional.of(ERROR_KB_NOT_CONFIGURED);
            // A1-4：MARKET/COMPANY 专库或 JD 库任一配置即可（未配置专库时回退 JD）
            case MARKET -> kbConfigured(ragForgeProperties.getMarketKbId()) || kbConfigured(ragForgeProperties.getJdKbId())
                    ? Optional.empty()
                    : Optional.of(ERROR_KB_NOT_CONFIGURED);
            case COMPANY -> kbConfigured(ragForgeProperties.getCompanyKbId()) || kbConfigured(ragForgeProperties.getJdKbId())
                    ? Optional.empty()
                    : Optional.of(ERROR_KB_NOT_CONFIGURED);
            case RESUME -> kbConfigured(ragForgeProperties.getPersonalKbId())
                    ? Optional.empty()
                    : Optional.of(ERROR_KB_NOT_CONFIGURED);
            case GENERAL -> {
                if (kbConfigured(ragForgeProperties.getJdKbId())
                        || kbConfigured(ragForgeProperties.getInterviewKbId())) {
                    yield Optional.empty();
                }
                yield Optional.of(ERROR_KB_NOT_CONFIGURED);
            }
        };
    }

    private boolean kbConfigured(String kbIdRaw) {
        return parseKbId(kbIdRaw) != null;
    }

    private Long parseKbId(String kbIdRaw) {
        if (!StringUtils.hasText(kbIdRaw)) {
            return null;
        }
        try {
            long kbId = Long.parseLong(kbIdRaw.trim());
            return kbId > 0 ? kbId : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<RagRetrievedChunk> mapChunks(List<RagForgeChunk> rawChunks, RagRetrieveScene scene) {
        List<RagRetrievedChunk> chunks = new ArrayList<>(rawChunks.size());
        for (RagForgeChunk raw : rawChunks) {
            if (raw == null) {
                continue;
            }
            String fileName = raw.filename();
            String content = raw.content() == null ? "" : raw.content();
            RagRetrieverChunkType chunkType = mapChunkType(raw.chunkType(), scene);
            chunks.add(RagRetrievedChunk.builder()
                    .content(content)
                    .contentPreview(KnowledgeRetrievalSupport.buildContentPreview(content, scene))
                    .citation(KnowledgeRetrievalSupport.buildCitation(fileName, raw.docId(), chunkType))
                    .chunkId(raw.chunkId())
                    .docId(raw.docId())
                    .sourceId(raw.docId() == null ? null : String.valueOf(raw.docId()))
                    .sourceTitle(fileName)
                    .fileName(fileName)
                    .score(raw.finalScore())
                    .chunkType(chunkType)
                    .metadata(buildChunkMetadata(raw))
                    .build());
        }
        return chunks;
    }

    private Map<String, Object> buildChunkMetadata(RagForgeChunk raw) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (raw.chunkType() != null) {
            metadata.put("rawChunkType", raw.chunkType());
        }
        return metadata;
    }

    private RagRetrieverChunkType mapChunkType(String rawType, RagRetrieveScene scene) {
        if (scene == RagRetrieveScene.COMPANY) {
            if (!StringUtils.hasText(rawType)) {
                return RagRetrieverChunkType.COMPANY;
            }
            String normalized = rawType.trim().toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "INTERVIEW_QA", "INTERVIEW", "QA" -> RagRetrieverChunkType.INTERVIEW_QA;
                case "MARKET_REPORT", "MARKET" -> RagRetrieverChunkType.MARKET_REPORT;
                case "RESUME", "PERSONAL_RESUME" -> RagRetrieverChunkType.RESUME;
                default -> RagRetrieverChunkType.COMPANY;
            };
        }
        if (!StringUtils.hasText(rawType)) {
            return defaultChunkType(scene);
        }
        String normalized = rawType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "JD", "JD_PATTERN" -> RagRetrieverChunkType.JD;
            case "INTERVIEW_QA", "INTERVIEW", "QA" -> RagRetrieverChunkType.INTERVIEW_QA;
            case "MARKET_REPORT", "MARKET" -> RagRetrieverChunkType.MARKET_REPORT;
            case "RESUME", "PERSONAL_RESUME" -> RagRetrieverChunkType.RESUME;
            case "COMPANY" -> RagRetrieverChunkType.COMPANY;
            default -> defaultChunkType(scene);
        };
    }

    private RagRetrieverChunkType defaultChunkType(RagRetrieveScene scene) {
        return switch (scene) {
            case INTERVIEW -> RagRetrieverChunkType.INTERVIEW_QA;
            case OPPORTUNITY -> RagRetrieverChunkType.JD;
            case MARKET -> RagRetrieverChunkType.MARKET_REPORT;
            case COMPANY -> RagRetrieverChunkType.COMPANY;
            case RESUME -> RagRetrieverChunkType.RESUME;
            case GENERAL -> RagRetrieverChunkType.GENERAL;
        };
    }

    private void appendDistinct(Map<String, RagRetrievedChunk> distinct, List<RagRetrievedChunk> chunks) {
        if (chunks == null) {
            return;
        }
        for (RagRetrievedChunk chunk : chunks) {
            if (chunk == null) {
                continue;
            }
            String key = chunk.getChunkId() != null
                    ? "id:" + chunk.getChunkId()
                    : "content:" + (chunk.getContent() == null ? "" : chunk.getContent());
            distinct.putIfAbsent(key, chunk);
        }
    }

    int normalizeTopK(int topK) {
        if (topK < 1) {
            return DEFAULT_TOP_K;
        }
        return Math.min(topK, MAX_TOP_K);
    }

    private long elapsed(long startMs) {
        return Math.max(0L, System.currentTimeMillis() - startMs);
    }
}
