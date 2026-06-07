package com.careermate.agent.tool;

import com.careermate.ragforge.RagForgeChunk;
import com.careermate.ragforge.RagForgeClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SearchKnowledgeBaseTool implements AgentTool {

    private static final int PREVIEW_CHARS = 200;
    private static final int DEFAULT_TOP_K = 5;

    private final RagForgeClient ragForgeClient;

    public SearchKnowledgeBaseTool(RagForgeClient ragForgeClient) {
        this.ragForgeClient = ragForgeClient;
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
    public boolean supports(AgentToolContext context) {
        return true;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        String query = extractQuery(context);
        if (query == null || query.isBlank()) {
            return AgentToolResult.failure(name(), "未识别到检索关键词", "query 为空");
        }
        List<RagForgeChunk> chunks = ragForgeClient.searchJd(query, DEFAULT_TOP_K);
        if (chunks.isEmpty()) {
            return AgentToolResult.failure(name(), "知识库暂无相关内容", "RAGForge 未启用或检索返回空");
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            RagForgeChunk c = chunks.get(i);
            String content = c.content() == null ? "" : c.content();
            String preview = content.length() > PREVIEW_CHARS
                ? content.substring(0, PREVIEW_CHARS) + "..."
                : content;
            sb.append(i + 1).append(". ").append(preview).append("\n");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("query", query);
        data.put("count", chunks.size());
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
