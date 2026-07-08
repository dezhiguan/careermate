package com.careermate.agent.reflect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * A3：从 LLM 输出中稳健提取 JSON（常被 ```json 围栏或散文包裹），并解析为 plan/reflection。
 * 解析失败一律返回安全默认，不抛异常（保证反思闭环不因脏输出中断，错误对用户不可见）。
 */
public final class ReflectionJsonSupport {

    private ReflectionJsonSupport() {
    }

    /** 抠出第一个 {...} JSON 片段；无则返回 null。 */
    public static JsonNode extractJson(ObjectMapper mapper, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return mapper.readTree(text.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }
    }

    public static List<String> stringList(JsonNode node, String field) {
        List<String> out = new ArrayList<>();
        if (node == null) {
            return out;
        }
        JsonNode arr = node.get(field);
        if (arr != null && arr.isArray()) {
            arr.forEach(n -> {
                String v = n.asText(null);
                if (v != null && !v.isBlank()) {
                    out.add(v.trim());
                }
            });
        }
        return out;
    }

    public static boolean boolField(JsonNode node, String field, boolean dft) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return dft;
        }
        return node.get(field).asBoolean(dft);
    }

    public static double doubleField(JsonNode node, String field, double dft) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return dft;
        }
        return node.get(field).asDouble(dft);
    }

    public static String textField(JsonNode node, String field, String dft) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return dft;
        }
        String v = node.get(field).asText(null);
        return (v == null || v.isBlank()) ? dft : v.trim();
    }
}
