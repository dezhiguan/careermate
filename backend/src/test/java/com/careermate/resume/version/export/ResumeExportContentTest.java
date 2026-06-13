package com.careermate.resume.version.export;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 校验渲染器输出「真有内容」而非空白：DOCX 通过抽取文本断言关键词，PDF 通过对比字节量断言非空壳。
 */
class ResumeExportContentTest {

    private final ResumeVersionPdfRenderer pdfRenderer = new ResumeVersionPdfRenderer();
    private final ResumeVersionDocxRenderer docxRenderer = new ResumeVersionDocxRenderer();

    private String docxText(String markdown) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        docxRenderer.render(markdown, out);
        byte[] bytes = out.toByteArray();
        assertEquals('P', (char) bytes[0], "应为 zip(PK) 开头的 docx");
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private byte[] pdfBytes(String markdown) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        pdfRenderer.render(markdown, out);
        return out.toByteArray();
    }

    @Test
    void docxFenceWrappedResumeIsNotBlank() throws Exception {
        String wrapped = "```markdown\n# 张三\n\n## 工作经历\n- 负责后端系统\n熟悉 **Java**\n```";
        String text = docxText(wrapped);
        assertTrue(text.contains("张三"), "应包含姓名");
        assertTrue(text.contains("工作经历"), "应包含标题");
        assertTrue(text.contains("负责后端系统"), "应包含列表项");
        assertTrue(text.contains("Java"), "应包含加粗文本");
    }

    @Test
    void docxRendersTableCells() throws Exception {
        String md = "## 技能\n\n| 技能 | 年限 |\n|---|---|\n| Java | 3 |\n| MySQL | 2 |";
        String text = docxText(md);
        assertTrue(text.contains("技能"));
        assertTrue(text.contains("年限"));
        assertTrue(text.contains("Java"));
        assertTrue(text.contains("MySQL"));
    }

    @Test
    void docxRendersOrderedListItalicAndLink() throws Exception {
        String md = "## 项目\n1. 第一项 *重点*\n2. 第二项\n\n[博客](https://example.com)";
        String text = docxText(md);
        assertTrue(text.contains("第一项"));
        assertTrue(text.contains("第二项"));
        assertTrue(text.contains("博客"));
        assertTrue(text.contains("example.com"), "链接应保留 URL");
    }

    @Test
    void docxPlainTextResumeIsNotBlank() throws Exception {
        String text = docxText("张三\n后端工程师\n电话 13800000000");
        assertTrue(text.contains("张三"));
        assertTrue(text.contains("后端工程师"));
    }

    @Test
    void docxEmptyContentUsesFallback() throws Exception {
        String text = docxText("");
        assertTrue(text.contains("简历内容为空"), "空内容应输出兜底提示而非全空");
    }

    @Test
    void pdfFenceWrappedRendersMoreThanEmpty() throws Exception {
        byte[] wrapped = pdfBytes("```markdown\n# 张三\n\n## 工作经历\n- 负责后端系统开发\n```");
        byte[] empty = pdfBytes("");
        assertEquals("%PDF", new String(wrapped, 0, 4), "应为合法 PDF");
        assertTrue(wrapped.length > empty.length + 200,
                "围栏内容应渲染出明显多于空文档的内容: wrapped=" + wrapped.length + " empty=" + empty.length);
    }

    @Test
    void pdfTableProducesValidPdf() throws Exception {
        byte[] bytes = pdfBytes("| a | b |\n|---|---|\n| 1 | 2 |");
        assertEquals("%PDF", new String(bytes, 0, 4));
        assertTrue(bytes.length > 800);
    }
}
