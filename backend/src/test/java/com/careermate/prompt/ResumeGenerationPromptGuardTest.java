package com.careermate.prompt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实际生效的简历生成提示词必须带反编造硬约束。
 *
 * <p>此处刻意走 {@link PromptTemplateService#render}，拿的是<b>配置覆盖后</b>真正会送给模型的那一版，
 * 而不是 prompt-manifest.json 里的 activeVersion。两者可以不一致：
 * {@code careermate.prompt.active-versions} 的默认值一直把生成提示词钉在某个版本上，
 * 只改清单不会生效——排查时正是因为只看了清单，误以为线上跑的是另一版，白改了两轮。
 *
 * <p>约束本身要防的是：生成器为贴合 JD 给候选人凭空加技能。线上把一位
 * Java/Go 候选人写成「熟练 TypeScript + Python 双栈，Python 做向量预处理与 RAG 文档切片，
 * 掌握 FastAPI」，源简历里这些词一个都没有。事实校验能拦下，但拦下就不落库，
 * 用户等三十多秒只拿到一张「需确认」卡片——所以生成端必须自己守住。
 */
@SpringBootTest
@ActiveProfiles("test")
class ResumeGenerationPromptGuardTest {

    @Autowired
    private PromptTemplateService promptTemplateService;

    @Test
    void 生效版本必须约束技术栈与数字不得新增() {
        PromptRenderResult rendered = promptTemplateService.render("resume-generate-from-jd");
        String prompt = rendered.content();

        assertTrue(prompt.contains("源简历"), "必须明确出处只能是源简历");
        assertTrue(prompt.contains("技术栈"), "必须点名约束技术栈，仅说「不许虚构经历」不够");
        assertTrue(prompt.contains("一个字都不要出现"),
                "缺失技术必须整份留白：留「熟悉 X 生态」这类口子，模型就会照写，然后被事实校验整份拦下");
        assertTrue(prompt.contains("数字") || prompt.contains("百分比"), "必须约束指标数字不得新造");
        assertTrue(prompt.contains("不是") && prompt.contains("对齐"),
                "必须说清「对齐 JD」不等于把 JD 要求搬进简历");
    }

    @Test
    void 生效版本仍保留既有的meta契约() {
        // 加护栏不能顺手把 meta 格式改了——anchor / suggestions 是下游解析依赖的
        String prompt = promptTemplateService.render("resume-generate-from-jd").content();
        assertTrue(prompt.contains("anchor"), "meta 契约里的 anchor 不能丢");
        assertTrue(prompt.contains("suggestions"), "meta 契约里的 suggestions 不能丢");
    }
}
