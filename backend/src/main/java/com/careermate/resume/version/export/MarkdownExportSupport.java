package com.careermate.resume.version.export;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简历导出（PDF / Word）共享的 Markdown 处理工具：
 * 统一的解析器（含 GFM 表格扩展）、整段代码围栏剥离，以及 LLM 优化说明 meta 剥离。
 */
public final class MarkdownExportSupport {

    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create());
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 首行形如 ``` 或 ```markdown / ~~~ 的整段围栏起始标记。 */
    private static final Pattern WRAPPING_FENCE_FIRST_LINE = Pattern.compile("(`{3,}|~{3,})[A-Za-z0-9_+-]*");
    private static final Pattern META_BLOCK_PATTERN = Pattern.compile(
            "```meta\\s*([\\s\\S]*?)```",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TRAILING_FENCED_JSON = Pattern.compile(
            "\\n*(```(?:json)?\\s*\\n([\\s\\S]*?)```)\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final int RESIDUAL_TAIL_WINDOW = 600;

    private MarkdownExportSupport() {
    }

    public record OptimizationMetaStripResult(String markdown, List<Map<String, Object>> changes) {
    }

    public static Parser parser() {
        return Parser.builder().extensions(EXTENSIONS).build();
    }

    /**
     * 去掉把整段内容包裹起来的代码围栏（常见于 LLM 输出 {@code ```markdown ... ```}）。
     * 仅当首行是单独的围栏标记、且闭合围栏位于文本末尾时才剥离，避免误伤正文里真正的代码块。
     */
    public static String stripWrappingFence(String markdown) {
        if (markdown == null) {
            return "";
        }
        String trimmed = markdown.strip();
        if (!trimmed.startsWith("```") && !trimmed.startsWith("~~~")) {
            return markdown;
        }
        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline < 0) {
            return markdown;
        }
        String firstLine = trimmed.substring(0, firstNewline).trim();
        if (!WRAPPING_FENCE_FIRST_LINE.matcher(firstLine).matches()) {
            return markdown;
        }
        String fence = firstLine.startsWith("~") ? "~~~" : "```";
        String body = trimmed.substring(firstNewline + 1);
        int closing = body.lastIndexOf(fence);
        if (closing < 0) {
            return markdown;
        }
        // 闭合围栏之后只能是空白，否则说明这是正文中的代码块而非整段包裹
        if (!body.substring(closing + fence.length()).isBlank()) {
            return markdown;
        }
        return body.substring(0, closing).strip();
    }

    /**
     * 从 LLM 输出中剥离优化说明 meta，返回纯简历 Markdown 与 changes 列表。
     * 支持 {@code ```meta}、末尾裸 JSON、{@code ```json} 包裹的 meta/changes。
     */
    public static OptimizationMetaStripResult stripOptimizationMeta(String raw) {
        if (raw == null) {
            return new OptimizationMetaStripResult("", List.of());
        }
        String markdown = raw;
        List<Map<String, Object>> changes = new ArrayList<>();

        Matcher metaMatcher = META_BLOCK_PATTERN.matcher(markdown);
        if (metaMatcher.find()) {
            changes.addAll(extractOptimizationChanges(metaMatcher.group(1).trim()));
            markdown = metaMatcher.replaceFirst("").trim();
        }

        markdown = stripWrappingFence(markdown);

        TrailingOptimizationStrip trailing = stripTrailingOptimizationJson(markdown);
        markdown = trailing.markdown();
        changes.addAll(trailing.changes());

        return new OptimizationMetaStripResult(markdown.strip(), List.copyOf(changes));
    }

    /** 导出层防御：仅返回剥离 meta 后的 Markdown。 */
    public static String stripOptimizationMetaFromMarkdown(String raw) {
        return stripOptimizationMeta(raw).markdown();
    }

    /** 质量检查兜底：正文末尾是否仍残留优化说明。 */
    public static boolean hasOptimizationMetaResidual(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return false;
        }
        if (META_BLOCK_PATTERN.matcher(markdown).find()) {
            return true;
        }
        if (!stripTrailingOptimizationJson(markdown.strip()).changes().isEmpty()) {
            return true;
        }
        String tail = markdown.length() > RESIDUAL_TAIL_WINDOW
                ? markdown.substring(markdown.length() - RESIDUAL_TAIL_WINDOW)
                : markdown;
        if (tail.contains("```meta") || tail.contains("```json")) {
            return true;
        }
        String compactTail = tail.replaceAll("\\s+", "");
        return compactTail.contains("\"changes\":[")
                || compactTail.contains("\"meta\":{")
                || compactTail.contains("'changes':")
                || compactTail.contains("'meta':");
    }

    private record TrailingOptimizationStrip(String markdown, List<Map<String, Object>> changes) {
    }

    private static TrailingOptimizationStrip stripTrailingOptimizationJson(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return new TrailingOptimizationStrip(markdown == null ? "" : markdown, List.of());
        }
        String trimmed = markdown.stripTrailing();

        Matcher fenceMatcher = TRAILING_FENCED_JSON.matcher(trimmed);
        if (fenceMatcher.find()) {
            String jsonContent = fenceMatcher.group(2).trim();
            List<Map<String, Object>> changes = extractOptimizationChanges(jsonContent);
            if (!changes.isEmpty()) {
                String without = trimmed.substring(0, fenceMatcher.start()).stripTrailing();
                return new TrailingOptimizationStrip(without, changes);
            }
        }

        int lastBrace = trimmed.lastIndexOf('}');
        if (lastBrace < 0) {
            return new TrailingOptimizationStrip(markdown, List.of());
        }
        if (!trimmed.substring(lastBrace + 1).isBlank()) {
            return new TrailingOptimizationStrip(markdown, List.of());
        }

        int openBrace = findMatchingOpenBrace(trimmed, lastBrace);
        if (openBrace < 0) {
            return new TrailingOptimizationStrip(markdown, List.of());
        }

        String jsonCandidate = trimmed.substring(openBrace, lastBrace + 1);
        List<Map<String, Object>> changes = extractOptimizationChanges(jsonCandidate);
        if (changes.isEmpty()) {
            return new TrailingOptimizationStrip(markdown, List.of());
        }

        String without = trimmed.substring(0, openBrace).stripTrailing();
        return new TrailingOptimizationStrip(without, changes);
    }

    private static int findMatchingOpenBrace(String text, int closeIndex) {
        int depth = 0;
        for (int i = closeIndex; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '}') {
                depth++;
            } else if (c == '{') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    static List<Map<String, Object>> extractOptimizationChanges(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            JsonNode changesNode = root.get("changes");
            if ((changesNode == null || !changesNode.isArray()) && root.has("meta")) {
                JsonNode meta = root.get("meta");
                if (meta != null && meta.isObject()) {
                    changesNode = meta.get("changes");
                }
            }
            if (changesNode == null || !changesNode.isArray()) {
                return List.of();
            }
            return parseChangesArray(changesNode);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static List<Map<String, Object>> parseChangesArray(JsonNode array) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode item : array) {
            if (item.isTextual()) {
                result.add(Map.of("text", item.asText()));
            } else if (item.isObject()) {
                Map<String, Object> cast = OBJECT_MAPPER.convertValue(item, new TypeReference<>() {});
                result.add(new LinkedHashMap<>(cast));
            }
        }
        return result;
    }
}
