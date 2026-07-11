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
import com.careermate.knowledge.KnowledgeRetrievalService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RagRetrieverTool implements AgentTool {

    public static final String TOOL_NAME = "rag_retriever";
    public static final int DEFAULT_TOP_K = 5;
    public static final int MAX_TOP_K = 20;
    public static final int MIN_TOP_K = 1;

    static final String ERROR_RAGFORGE_DISABLED = KnowledgeRetrievalService.ERROR_RAGFORGE_DISABLED;
    static final String ERROR_KB_NOT_CONFIGURED = KnowledgeRetrievalService.ERROR_KB_NOT_CONFIGURED;
    static final String ERROR_EMPTY_RESULTS = KnowledgeRetrievalService.ERROR_EMPTY_RESULTS;
    static final String ERROR_QUERY_MISSING = KnowledgeRetrievalService.ERROR_QUERY_MISSING;

    private final KnowledgeRetrievalService knowledgeRetrievalService;

    public RagRetrieverTool(KnowledgeRetrievalService knowledgeRetrievalService) {
        this.knowledgeRetrievalService = knowledgeRetrievalService;
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
                        .description("检索场景：OPPORTUNITY / INTERVIEW / MARKET / COMPANY / RESUME / SKILL / GENERAL")
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
                ? "知识库暂未返回相关内容，本次先用通用能力作答。"
                : switch (result.getErrorCode()) {
            case ERROR_RAGFORGE_DISABLED -> "知识检索暂未启用，本次先用通用能力作答。";
            case ERROR_KB_NOT_CONFIGURED -> "该场景的知识库还没配置好，本次先用通用能力作答，结果可能不够精准。";
            case ERROR_EMPTY_RESULTS -> "知识库里暂时没找到相关内容，换个说法或关键词也许更好。";
            default -> "知识库暂未返回相关内容，本次先用通用能力作答。";
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
        return knowledgeRetrievalService.retrieve(request);
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
        row.put("contentPreview", chunk.getContentPreview());
        row.put("citation", chunk.getCitation());
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
}
