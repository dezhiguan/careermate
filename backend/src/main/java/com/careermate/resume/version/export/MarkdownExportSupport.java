package com.careermate.resume.version.export;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 简历导出（PDF / Word）共享的 Markdown 处理工具：
 * 统一的解析器（含 GFM 表格扩展）与「整段代码围栏」剥离。
 */
public final class MarkdownExportSupport {

    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create());

    /** 首行形如 ``` 或 ```markdown / ~~~ 的整段围栏起始标记。 */
    private static final Pattern WRAPPING_FENCE_FIRST_LINE = Pattern.compile("(`{3,}|~{3,})[A-Za-z0-9_+-]*");

    private MarkdownExportSupport() {
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
}
