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
