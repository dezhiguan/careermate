package com.careermate.resume.version.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

/**
 * Word · ATS 版渲染 smoke 测试——验证 WORD_ATS 预设可端到端渲染出合法 docx。
 */
class ResumeVersionDocxRendererTest {

    private final ResumeVersionDocxRenderer renderer = new ResumeVersionDocxRenderer();

    @Test
    void rendersNonEmptyDocxWithZipHeader() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        renderer.render("# 张三\n\n## 工作经历\n\n腾讯 — 高级工程师（2021-至今）\n\n- 主导交易系统重构", out);

        byte[] bytes = out.toByteArray();
        assertThat(bytes.length).isGreaterThan(0);
        // docx 本质是 zip，魔数为 'P''K'
        assertThat(bytes[0]).isEqualTo((byte) 'P');
        assertThat(bytes[1]).isEqualTo((byte) 'K');
    }

    @Test
    void rendersPlainTextFallbackWithoutSections() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        renderer.render("一段没有任何标题的纯文本简历内容。", out);
        assertThat(out.toByteArray().length).isGreaterThan(0);
    }
}
