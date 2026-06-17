package com.careermate.agent.multiagent;

import com.careermate.agent.tool.AgentToolResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SpecialistResultSupport {

    private static final int QUERY_PREVIEW_MAX = 120;

    private SpecialistResultSupport() {
    }

    static Map<String, Object> safeToolData(AgentToolResult result) {
        if (result == null || result.getData() == null || result.getData().isEmpty()) {
            Map<String, Object> minimal = new LinkedHashMap<>();
            minimal.put("toolName", result == null ? null : result.getToolName());
            minimal.put("success", result != null && result.isSuccess());
            return minimal;
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        safe.put("toolName", result.getToolName());
        safe.put("success", result.isSuccess());
        copyIfPresent(safe, result.getData(), "count", "chunkCount", "scene", "query", "queryLength",
                "workflowId", "artifactId", "fallbackUsed", "errorCode", "latencyMs");
        if (result.getData().containsKey("query") && result.getData().get("query") instanceof String query) {
            safe.put("queryPreview", preview(query));
            safe.remove("query");
        }
        return safe;
    }

    static List<String> extractCitations(Map<String, Object> data) {
        if (data == null) {
            return List.of();
        }
        Object previews = data.get("previews");
        if (previews instanceof List<?> previewList) {
            List<String> citations = new ArrayList<>();
            for (Object item : previewList) {
                if (item instanceof Map<?, ?> row) {
                    appendCitation(citations, row.get("citation"));
                }
            }
            if (!citations.isEmpty()) {
                return citations;
            }
        }
        Object chunks = data.get("chunks");
        if (!(chunks instanceof List<?> chunkList)) {
            return List.of();
        }
        List<String> citations = new ArrayList<>();
        for (Object chunk : chunkList) {
            if (chunk instanceof Map<?, ?> row) {
                appendCitation(citations, row.get("citation"));
            }
        }
        return citations;
    }

    static String preview(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= QUERY_PREVIEW_MAX) {
            return trimmed;
        }
        return trimmed.substring(0, QUERY_PREVIEW_MAX) + "...";
    }

    private static void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String... keys) {
        for (String key : keys) {
            if (source.containsKey(key)) {
                target.put(key, source.get(key));
            }
        }
    }

    private static void appendCitation(List<String> citations, Object citation) {
        if (citation == null) {
            return;
        }
        String value = String.valueOf(citation).trim();
        if (!value.isBlank() && !citations.contains(value)) {
            citations.add(value);
        }
    }
}
