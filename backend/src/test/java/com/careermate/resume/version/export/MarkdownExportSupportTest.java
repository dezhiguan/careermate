package com.careermate.resume.version.export;

import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.Node;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownExportSupportTest {

    @Test
    void nullReturnsEmpty() {
        assertEquals("", MarkdownExportSupport.stripWrappingFence(null));
    }

    @Test
    void plainMarkdownUnchanged() {
        String md = "# 标题\n\n这是正文，没有任何代码围栏。";
        assertEquals(md, MarkdownExportSupport.stripWrappingFence(md));
    }

    @Test
    void stripsMarkdownLanguageFence() {
        String wrapped = "```markdown\n# 张三\n\n## 工作经历\n```";
        assertEquals("# 张三\n\n## 工作经历", MarkdownExportSupport.stripWrappingFence(wrapped));
    }

    @Test
    void stripsPlainTripleBacktickFence() {
        String wrapped = "```\n# 张三\n正文\n```";
        assertEquals("# 张三\n正文", MarkdownExportSupport.stripWrappingFence(wrapped));
    }

    @Test
    void stripsTildeFence() {
        String wrapped = "~~~\n# 张三\n~~~";
        assertEquals("# 张三", MarkdownExportSupport.stripWrappingFence(wrapped));
    }

    @Test
    void doesNotStripWhenFenceNotWrappingWhole() {
        // 以代码块开头，但闭合围栏后还有正文 —— 属于正文中的代码块，不应剥离
        String md = "```\ncode\n```\n\n这里还有正文";
        assertEquals(md, MarkdownExportSupport.stripWrappingFence(md));
    }

    @Test
    void doesNotStripWhenNoClosingFence() {
        String md = "```markdown\n# 张三\n正文(无闭合围栏)";
        assertEquals(md, MarkdownExportSupport.stripWrappingFence(md));
    }

    @Test
    void parsesV3ChangesWithAnchorAndSuggestions() {
        String raw = "# 张三\n## 专业技能\nJava、Go\n\n```meta\n{\"change_summary\":\"做了改动\","
                + "\"changes\":[{\"reason\":\"补全技能\",\"anchor\":\"Java、Go\"}],"
                + "\"suggestions\":[{\"text\":\"弱化前端\",\"anchor\":\"React\"}]}\n```";
        var result = MarkdownExportSupport.stripOptimizationMeta(raw);
        // changes 含 1 个改动 + 1 个 suggestion(kind=suggestion)
        assertEquals(2, result.changes().size());
        var change = result.changes().get(0);
        assertEquals("补全技能", change.get("reason"));
        assertEquals("Java、Go", change.get("anchor"));
        var sug = result.changes().get(1);
        assertEquals("suggestion", sug.get("kind"));
        assertEquals("弱化前端", sug.get("text"));
        assertEquals("React", sug.get("anchor"));
        assertTrue(result.markdown().contains("专业技能"));
    }

    @Test
    void parserSupportsGfmTables() {
        Node doc = MarkdownExportSupport.parser().parse("| a | b |\n|---|---|\n| 1 | 2 |");
        boolean[] hasTable = {false};
        doc.accept(new AbstractVisitor() {
            @Override
            public void visit(CustomBlock customBlock) {
                if (customBlock instanceof TableBlock) {
                    hasTable[0] = true;
                }
                super.visit(customBlock);
            }
        });
        assertTrue(hasTable[0], "解析器应启用 GFM 表格扩展");
    }
}
