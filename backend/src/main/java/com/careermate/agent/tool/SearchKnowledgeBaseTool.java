package com.careermate.agent.tool;

import com.careermate.agent.tool.rag.RagRetrieveRequest;
import com.careermate.agent.tool.rag.RagRetrieveScene;
import com.careermate.agent.tool.rag.RagRetrieveResult;
import com.careermate.agent.tool.rag.RagRetrievedChunk;
import com.careermate.knowledge.KnowledgeRetrievalService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SearchKnowledgeBaseTool implements AgentTool {

    private static final int DEFAULT_TOP_K = 5;

    private final KnowledgeRetrievalService knowledgeRetrievalService;

    public SearchKnowledgeBaseTool(KnowledgeRetrievalService knowledgeRetrievalService) {
        this.knowledgeRetrievalService = knowledgeRetrievalService;
    }

    @Override
    public String name() {
        return "search_knowledge_base";
    }

    @Override
    public String description() {
        return "在 RAGForge 知识库（默认 JD Pattern KB）中搜索相关岗位/行业参考，返回相关片段列表";
    }

    @Override
    public AgentToolDefinition definition() {
        return AgentToolDefinition.base(
                name(),
                "搜索知识库",
                description(),
                AgentToolDomain.KNOWLEDGE,
                AgentToolPermission.CALL_EXTERNAL_SERVICE,
                AgentToolRiskLevel.MEDIUM
        )
                .parameter(AgentToolDefinitionSupport.stringParam(
                        "query", false, "检索关键词；缺失时可从用户消息推断"))
                .example("搜索类似岗位要求")
                .build();
    }

    @Override
    public boolean supports(AgentToolContext context) {
        return true;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        String query = extractQuery(context);
        if (query == null || query.isBlank()) {
            return AgentToolResult.failure(name(), "未识别到检索关键词", "query 为空");
        }
        RagRetrieveResult result = knowledgeRetrievalService.retrieve(RagRetrieveRequest.builder()
                .query(query)
                .scene(RagRetrieveScene.OPPORTUNITY)
                .topK(DEFAULT_TOP_K)
                .build());
        if (!result.isSuccess() || result.getChunks().isEmpty()) {
            return AgentToolResult.failure(name(), "知识库暂无相关内容", "RAGForge 未启用或检索返回空");
        }

        List<RagRetrievedChunk> chunks = result.getChunks();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            RagRetrievedChunk chunk = chunks.get(i);
            String preview = chunk.getContentPreview() == null ? "" : chunk.getContentPreview();
            String citation = chunk.getCitation() == null ? "" : chunk.getCitation();
            sb.append(i + 1).append(". [").append(citation).append("] ").append(preview).append("\n");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("query", query);
        data.put("count", chunks.size());
        data.put("previews", chunks.stream().map(chunk -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("contentPreview", chunk.getContentPreview());
            row.put("citation", chunk.getCitation());
            row.put("score", chunk.getScore());
            row.put("chunkType", chunk.getChunkType() == null ? null : chunk.getChunkType().name());
            return row;
        }).toList());
        return AgentToolResult.success(name(),
            "已从 RAGForge 检索到 " + chunks.size() + " 条相关片段", data);
    }

    private String extractQuery(AgentToolContext context) {
        Map<String, Object> args = context.getArgs();
        if (args != null) {
            Object v = args.get("query");
            if (v instanceof String s && !s.isBlank()) {
                return s.trim();
            }
        }
        String msg = context.getUserMessage();
        if (msg == null || msg.isBlank()) {
            return null;
        }
        return msg.length() > 100 ? msg.substring(0, 100) : msg;
    }
}
