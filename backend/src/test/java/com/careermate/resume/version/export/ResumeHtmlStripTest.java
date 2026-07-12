package com.careermate.resume.version.export;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BUG-16 回归：块级 HTML 剥标签后文本不丢失，实体正确还原。
 */
class ResumeHtmlStripTest {

    @Test
    void stripsTagsButKeepsText() {
        String out = ResumeVersionPdfRenderer.stripHtmlTags("<div>我是一名资深后端工程师</div>");
        assertTrue(out.contains("我是一名资深后端工程师"), "标签内文本应保留: " + out);
    }

    @Test
    void decodesCommonEntities() {
        String out = ResumeVersionPdfRenderer.stripHtmlTags("A &amp; B &lt;C&gt; &quot;D&quot;");
        assertEquals("A & B <C> \"D\"", out);
    }

    @Test
    void dropsScriptContent() {
        String out = ResumeVersionPdfRenderer.stripHtmlTags("<script>alert(1)</script>正文");
        assertTrue(out.contains("正文"));
        assertTrue(!out.contains("alert"), "script 内容应被剔除: " + out);
    }
}
