package com.careermate.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生效的简历生成提示词必须带反编造硬约束。
 *
 * <p>此前生效的 v1 只有一句「不许虚构经历」，只覆盖经历、不覆盖技能，而规则①「关键词对齐 JD」
 * 反过来还在推着模型往简历里塞 JD 关键词。线上因此把一位 Java/Go 候选人写成
 * 「熟练 TypeScript + Python 双栈，用 TypeScript(Node.js) 构建管理端，Python 做向量预处理与
 * RAG 文档切片，掌握 FastAPI」——源简历里这些词一个都没有。
 *
 * <p>事实校验能拦下这类编造，但拦下就意味着不落库：护栏挡住了脏水，水龙头没关，
 * 用户等三十多秒只拿到一张「需确认」卡片。所以生成端必须自己守住。
 */
class ResumeGenerationPromptGuardTest {

    @Test
    void 生效版本必须约束技术栈与数字不得新增() throws Exception {
        JsonNode manifest = new ObjectMapper().readTree(
                new ClassPathResource("prompts/prompt-manifest.json").getInputStream());
        String active = manifest.path("prompts").path("resume-generate-from-jd").path("activeVersion").asText();

        String prompt = new String(
                new ClassPathResource("prompts/resume-generate-from-jd/" + active + ".md")
                        .getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertTrue(prompt.contains("技术栈"), "必须点名约束技术栈，仅说「不许虚构经历」不够");
        assertTrue(prompt.contains("源简历"), "必须明确出处只能是源简历");
        assertTrue(prompt.contains("数字") || prompt.contains("百分比"), "必须约束指标数字不得新造");
        assertTrue(prompt.contains("不是") && prompt.contains("对齐"),
                "必须说清「对齐 JD」不等于把 JD 要求搬进简历");
    }
}
