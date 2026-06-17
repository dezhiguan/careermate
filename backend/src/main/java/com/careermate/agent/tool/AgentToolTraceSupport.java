package com.careermate.agent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AgentToolTraceSupport {

    private AgentToolTraceSupport() {
    }

    public static String buildRequestSummary(String toolName, Map<String, Object> args, String userMessage) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("toolName", toolName);
        if (args != null && !args.isEmpty()) {
            Map<String, Object> safeArgs = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : args.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if ("jdContent".equals(key) && value != null) {
                    String text = String.valueOf(value);
                    safeArgs.put("jdContentLength", text.length());
                } else if ("query".equals(key) && value != null) {
                    safeArgs.put("queryLength", String.valueOf(value).length());
                } else {
                    safeArgs.put(key, value);
                }
            }
            summary.put("args", safeArgs);
        }
        if (userMessage != null) {
            summary.put("userMessageLength", userMessage.length());
        }
        return toJson(summary);
    }

    public static String buildResponseSummary(AgentToolResult result, ObjectMapper objectMapper) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("success", result.isSuccess());
        summary.put("summary", result.getSummary());
        if (result.getErrorMessage() != null) {
            summary.put("errorMessage", result.getErrorMessage());
        }
        if (result.getData() != null) {
            Map<String, Object> safeData = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : result.getData().entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if ("contentPreview".equals(key) && value != null) {
                    String text = String.valueOf(value);
                    safeData.put("contentPreviewLength", text.length());
                } else if ("chunks".equals(key) && value instanceof java.util.List<?> list) {
                    safeData.put("chunkCount", list.size());
                } else if ("chunkCount".equals(key) || "fallbackUsed".equals(key)
                        || "latencyMs".equals(key) || "errorCode".equals(key) || "scene".equals(key)) {
                    safeData.put(key, value);
                } else if ("query".equals(key) && value != null) {
                    safeData.put("queryLength", String.valueOf(value).length());
                } else if ("content".equals(key) && value != null) {
                    safeData.put("contentLength", String.valueOf(value).length());
                } else if ("jdContent".equals(key) && value != null) {
                    safeData.put("jdContentLength", String.valueOf(value).length());
                } else if ("tasks".equals(key) && value instanceof java.util.List<?> list) {
                    safeData.put("tasks", sanitizeTaskList(list));
                } else if ("description".equals(key) && value != null) {
                    String text = String.valueOf(value);
                    safeData.put("descriptionLength", text.length());
                } else {
                    safeData.put(key, value);
                }
            }
            summary.put("data", safeData);
        }
        return toJson(summary, objectMapper);
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<Map<String, Object>> sanitizeTaskList(java.util.List<?> list) {
        java.util.List<Map<String, Object>> safe = new java.util.ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            Object taskId = raw.get("taskId");
            if (taskId == null) {
                taskId = raw.get("id");
            }
            if (taskId != null) {
                row.put("taskId", taskId);
            }
            Object title = raw.get("title");
            if (title != null) {
                row.put("title", title);
            }
            if (!row.isEmpty()) {
                safe.add(row);
            }
        }
        return safe;
    }

    private static String toJson(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(entry.getKey()).append("\":");
            appendValue(sb, entry.getValue());
        }
        sb.append('}');
        return sb.toString();
    }

    private static String toJson(Map<String, Object> data, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return toJson(data);
        }
    }

    @SuppressWarnings("unchecked")
    private static void appendValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map<?, ?> map) {
            sb.append(toJson((Map<String, Object>) map));
        } else {
            sb.append('"').append(String.valueOf(value).replace("\"", "\\\"")).append('"');
        }
    }
}
