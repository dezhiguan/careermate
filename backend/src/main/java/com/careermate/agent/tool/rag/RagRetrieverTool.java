package com.careermate.agent.tool.rag;

import com.careermate.agent.tool.AgentTool;
import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.AgentToolDefinition;
import com.careermate.agent.tool.AgentToolDomain;
import com.careermate.agent.tool.AgentToolParameter;
import com.careermate.agent.tool.AgentToolParameterType;
import com.careermate.agent.tool.AgentToolPermission;
import com.careermate.agent.tool.AgentToolResult;
import com.careermate.agent.tool.AgentToolRiskLevel;
import com.careermate.ragforge.RagForgeChunk;
import com.careermate.ragforge.RagForgeClient;
import com.careermate.ragforge.RagForgeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class RagRetrieverTool implements AgentTool {

    public static final String TOOL_NAME = "rag_retriever";
    public static final int DEFAULT_TOP_K = 5;
    public static final int MAX_TOP_K = 20;
    public static final int MIN_TOP_K = 1;

    static final String ERROR_RAGFORGE_DISABLED = "RAGFORGE_DISABLED";
    static final String ERROR_KB_NOT_CONFIGURED = "KB_NOT_CONFIGURED";
    static final String ERROR_EMPTY_RESULTS = "EMPTY_RESULTS";
    static final String ERROR_QUERY_MISSING = "QUERY_MISSING";

    private final RagForgeClient ragForgeClient;
    private final RagForgeProperties ragForgeProperties;

    public RagRetrieverTool(RagForgeClient ragForgeClient, RagForgeProperties ragForgeProperties) {
        this.ragForgeClient = ragForgeClient;
        this.ragForgeProperties = ragForgeProperties;
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "从 RAGForge 知识库检索与求职场景相关的参考片段，返回来源、分数和 chunk 类型";
    }

    @Override
    public AgentToolDefinition definition() {
        return AgentToolDefinition.base(
                name(),
                "RAG 知识检索",
                description(),
                AgentToolDomain.KNOWLEDGE,
                AgentToolPermission.CALL_EXTERNAL_SERVICE,
                AgentToolRiskLevel.MEDIUM
        )
                .parameter(AgentToolParameter.builder()
                        .name("query")
                        .type(AgentToolParameterType.STRING)
                        .required(false)
                        .description("检索问题或关键词；缺失时可从用户消息推断")
                        .build())
                .parameter(AgentToolParameter.builder()
                        .name("scene")
                        .type(AgentToolParameterType.STRING)
                        .required(false)
                        .description("检索场景：OPPORTUNITY / INTERVIEW / MARKET / RESUME / GENERAL")
                        .build())
                .parameter(AgentToolParameter.builder()
                        .name("topK")
                        .type(AgentToolParameterType.NUMBER)
                        .required(false)
                        .description("返回条数，默认 5，最大 20")
                        .build())
                .example("帮我查一下 Redis 缓存一致性面试题")
                .build();
    }

    @Override
    public boolean supports(AgentToolContext context) {
        return true;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        RagRetrieveRequest request = buildRequest(context);
        if (!StringUtils.hasText(request.getQuery())) {
            return structuredFailure(
                    RagRetrieveResult.fallback(null, request.getScene(), ERROR_QUERY_MISSING, 0),
                    "未识别到检索关键词"
            );
        }
        RagRetrieveResult result = retrieve(request);
        Map<String, Object> data = toToolData(result);
        if (result.isSuccess()) {
            return AgentToolResult.success(
                    name(),
                    "已从 RAGForge 检索到 " + result.getChunks().size() + " 条参考片段",
                    data
            );
        }
        String summary = result.getErrorCode() == null
                ? "RAG 检索未返回结果"
                : switch (result.getErrorCode()) {
            case ERROR_RAGFORGE_DISABLED -> "RAGForge 未启用，已降级";
            case ERROR_KB_NOT_CONFIGURED -> "当前场景知识库未配置，已降级";
            case ERROR_EMPTY_RESULTS -> "知识库暂无相关内容";
            default -> "RAG 检索未返回结果";
        };
        return AgentToolResult.builder()
                .toolName(name())
                .success(false)
                .summary(summary)
                .data(data)
                .errorMessage(result.getErrorCode())
                .build();
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
            List<RagForgeChunk> rawChunks = searchByScene(scene, query, topK, request.chunkTypeFilters());
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
            log.warn("rag_retriever failed: scene={} queryLen={} err={}", scene, query.length(), e.getMessage());
            return RagRetrieveResult.fallback(query, scene, ERROR_EMPTY_RESULTS, elapsed(start));
        }
    }

    private RagRetrieveRequest buildRequest(AgentToolContext context) {
        Map<String, Object> args = context.getArgs() == null ? Map.of() : context.getArgs();
        String query = extractQuery(args, context.getUserMessage());
        RagRetrieveScene scene = RagRetrieveScene.fromValue(stringArg(args, "scene"));
        int topK = parseTopK(args.get("topK"));
        return RagRetrieveRequest.builder()
                .query(query)
                .scene(scene)
                .topK(topK)
                .filters(Map.of())
                .build();
    }

    private List<RagForgeChunk> searchByScene(
            RagRetrieveScene scene,
            String query,
            int topK,
            List<String> chunkTypes
    ) {
        return switch (scene) {
            case INTERVIEW -> ragForgeClient.searchInterview(query, topK);
            case OPPORTUNITY, MARKET -> ragForgeClient.searchJd(query, topK);
            case RESUME -> searchKbId(ragForgeProperties.getPersonalKbId(), query, topK, chunkTypes);
            case GENERAL -> searchGeneral(query, topK, chunkTypes);
        };
    }

    private List<RagForgeChunk> searchGeneral(String query, int topK, List<String> chunkTypes) {
        List<RagForgeChunk> jdChunks = ragForgeClient.searchJd(query, topK);
        if (!jdChunks.isEmpty()) {
            return jdChunks;
        }
        return searchKbId(ragForgeProperties.getPersonalKbId(), query, topK, chunkTypes);
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
            case OPPORTUNITY, MARKET -> kbConfigured(ragForgeProperties.getJdKbId())
                    ? Optional.empty()
                    : Optional.of(ERROR_KB_NOT_CONFIGURED);
            case RESUME -> kbConfigured(ragForgeProperties.getPersonalKbId())
                    ? Optional.empty()
                    : Optional.of(ERROR_KB_NOT_CONFIGURED);
            case GENERAL -> {
                if (kbConfigured(ragForgeProperties.getJdKbId())
                        || kbConfigured(ragForgeProperties.getPersonalKbId())
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
            chunks.add(RagRetrievedChunk.builder()
                    .content(raw.content())
                    .chunkId(raw.chunkId())
                    .docId(raw.docId())
                    .sourceId(raw.docId() == null ? null : String.valueOf(raw.docId()))
                    .sourceTitle(fileName)
                    .fileName(fileName)
                    .score(raw.finalScore())
                    .chunkType(mapChunkType(raw.chunkType(), scene))
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
        if (!StringUtils.hasText(rawType)) {
            return defaultChunkType(scene);
        }
        String normalized = rawType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "JD", "JD_PATTERN" -> RagRetrieverChunkType.JD;
            case "INTERVIEW_QA", "INTERVIEW", "QA" -> RagRetrieverChunkType.INTERVIEW_QA;
            case "MARKET_REPORT", "MARKET" -> RagRetrieverChunkType.MARKET_REPORT;
            case "RESUME", "PERSONAL_RESUME" -> RagRetrieverChunkType.RESUME;
            default -> defaultChunkType(scene);
        };
    }

    private RagRetrieverChunkType defaultChunkType(RagRetrieveScene scene) {
        return switch (scene) {
            case INTERVIEW -> RagRetrieverChunkType.INTERVIEW_QA;
            case OPPORTUNITY -> RagRetrieverChunkType.JD;
            case MARKET -> RagRetrieverChunkType.MARKET_REPORT;
            case RESUME -> RagRetrieverChunkType.RESUME;
            case GENERAL -> RagRetrieverChunkType.GENERAL;
        };
    }

    Map<String, Object> toToolData(RagRetrieveResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("success", result.isSuccess());
        data.put("query", result.getQuery());
        data.put("scene", result.getScene() == null ? null : result.getScene().name());
        data.put("chunks", result.getChunks().stream().map(this::chunkToMap).toList());
        data.put("chunkCount", result.getChunks().size());
        data.put("fallbackUsed", result.isFallbackUsed());
        data.put("errorCode", result.getErrorCode());
        data.put("latencyMs", result.getLatencyMs());
        return data;
    }

    private Map<String, Object> chunkToMap(RagRetrievedChunk chunk) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("content", chunk.getContent());
        row.put("chunkId", chunk.getChunkId());
        row.put("docId", chunk.getDocId());
        row.put("sourceId", chunk.getSourceId());
        row.put("sourceTitle", chunk.getSourceTitle());
        row.put("fileName", chunk.getFileName());
        row.put("score", chunk.getScore());
        row.put("chunkType", chunk.getChunkType() == null ? null : chunk.getChunkType().name());
        if (chunk.getMetadata() != null && !chunk.getMetadata().isEmpty()) {
            row.put("metadata", chunk.getMetadata());
        }
        return row;
    }

    private AgentToolResult structuredFailure(RagRetrieveResult result, String summary) {
        return AgentToolResult.builder()
                .toolName(name())
                .success(false)
                .summary(summary)
                .data(toToolData(result))
                .errorMessage(result.getErrorCode())
                .build();
    }

    private String extractQuery(Map<String, Object> args, String userMessage) {
        String fromArgs = stringArg(args, "query");
        if (StringUtils.hasText(fromArgs)) {
            return fromArgs.trim();
        }
        if (!StringUtils.hasText(userMessage)) {
            return null;
        }
        String trimmed = userMessage.trim();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
    }

    private String stringArg(Map<String, Object> args, String key) {
        if (args == null || !args.containsKey(key) || args.get(key) == null) {
            return null;
        }
        return String.valueOf(args.get(key));
    }

    private int parseTopK(Object raw) {
        if (raw == null) {
            return DEFAULT_TOP_K;
        }
        if (raw instanceof Number number) {
            return normalizeTopK(number.intValue());
        }
        try {
            return normalizeTopK(Integer.parseInt(String.valueOf(raw).trim()));
        } catch (NumberFormatException e) {
            return DEFAULT_TOP_K;
        }
    }

    int normalizeTopK(int topK) {
        if (topK < MIN_TOP_K) {
            return DEFAULT_TOP_K;
        }
        return Math.min(topK, MAX_TOP_K);
    }

    private long elapsed(long startMs) {
        return Math.max(0L, System.currentTimeMillis() - startMs);
    }
}
