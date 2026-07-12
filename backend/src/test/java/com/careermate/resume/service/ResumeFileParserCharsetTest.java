package com.careermate.resume.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BUG-17 回归：GBK/GB18030 编码的 Markdown 不再被误判为“扫描件”，中文正常解出；
 * UTF-8（含 BOM）也正常。
 */
class ResumeFileParserCharsetTest {

    private final ResumeFileParserService service = new ResumeFileParserService();

    @Test
    void decodesGbkMarkdown() {
        byte[] gbk = "# 官德志\n工作经历 负责核心交易系统".getBytes(Charset.forName("GB18030"));
        MockMultipartFile file = new MockMultipartFile("file", "resume.md", "text/markdown", gbk);
        String text = service.parse(file);
        assertTrue(text.contains("官德志") && text.contains("核心交易系统"),
                "GBK 中文应正常解出: " + text);
    }

    @Test
    void decodesUtf8MarkdownWithBom() {
        byte[] body = "# 张三\n技能 Java".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF; withBom[1] = (byte) 0xBB; withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        MockMultipartFile file = new MockMultipartFile("file", "resume.md", "text/markdown", withBom);
        String text = service.parse(file);
        assertTrue(text.startsWith("# 张三"), "UTF-8 BOM 应被剥除且开头正常: " + text);
    }
}
