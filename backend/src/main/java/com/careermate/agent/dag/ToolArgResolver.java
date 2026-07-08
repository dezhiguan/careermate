package com.careermate.agent.dag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * B2：解析工具参数里的占位符 {@code $id[idx].field}，从上游结果替换为真实值。
 * 无法解析时保留原字符串（不抛异常）。
 */
public final class ToolArgResolver {

    // $id  或  $id[0]  或  $id.field  或  $id[0].field
    private static final Pattern REF = Pattern.compile("^\\$([A-Za-z0-9_\\-]+)(?:\\[(\\d+)])?(?:\\.([A-Za-z0-9_\\-]+))?$");

    private ToolArgResolver() {
    }

    public static Map<String, Object> resolve(Map<String, Object> args, Map<String, Object> upstream) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (args == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : args.entrySet()) {
            out.put(e.getKey(), resolveValue(e.getValue(), upstream));
        }
        return out;
    }

    private static Object resolveValue(Object value, Map<String, Object> upstream) {
        if (!(value instanceof String s) || !s.startsWith("$")) {
            return value;
        }
        Matcher m = REF.matcher(s);
        if (!m.matches()) {
            return value;
        }
        Object node = upstream == null ? null : upstream.get(m.group(1));
        if (node == null) {
            return value; // 未解析到，保留占位
        }
        if (m.group(2) != null && node instanceof List<?> list) {
            int idx = Integer.parseInt(m.group(2));
            node = idx >= 0 && idx < list.size() ? list.get(idx) : null;
        }
        if (m.group(3) != null && node instanceof Map<?, ?> map) {
            node = map.get(m.group(3));
        }
        return node != null ? node : value;
    }
}
