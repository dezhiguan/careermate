package com.careermate.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptTemplateRegistryTest {

    private final PromptTemplateRegistry registry = new PromptTemplateRegistry();

    @Test
    void loadsManifestAndResolvesKnownPrompt() {
        PromptTemplate template = registry.get("agent-base", "v1");

        assertEquals("agent-base", template.promptId());
        assertEquals("v1", template.version());
        assertTrue(template.content().contains("CareerMate"));
        assertTrue(template.content().contains("小职"));
    }

    @Test
    void manifestDefaultActiveVersionForResumeGenerate() {
        // v4 起把反编造约束从「不许虚构经历」扩到技术栈与指标数字，见 ResumeGenerationPromptGuardTest
        assertEquals("v5", registry.getManifestActiveVersion("resume-generate-from-jd"));
    }

    @Test
    void configOverrideSwitchesResumePromptVersion() {
        PromptProperties properties = new PromptProperties();
        properties.getActiveVersions().put("resume-generate-from-jd", "v2");
        PromptTemplateService service = new PromptTemplateService(registry, properties);

        PromptRenderResult result = service.render("resume-generate-from-jd");

        assertEquals("resume-generate-from-jd", result.promptId());
        assertEquals("v2", result.version());
        assertTrue(result.content().contains("模板 v2"));
    }

    @Test
    void defaultActiveVersionUsedWhenNoConfigOverride() {
        PromptTemplateService service = new PromptTemplateService(registry, new PromptProperties());

        PromptRenderResult result = service.render("resume-generate-from-jd");

        assertEquals("v5", result.version());
        assertFalse(result.content().contains("模板 v2"));
    }

    @Test
    void resumeGenerateV3IsRegisteredForDefaultConfig() {
        // 回归护栏：application.yml 默认 RESUME_PROMPT_VERSION=v3。若只在 prompts 目录放了 v3.md
        // 却忘了把 "v3" 加进 prompt-manifest.json 的 versions 数组，registry 不会加载它，
        // render 会抛 "Prompt version not found"，导致生产简历生成/修改全线失败（见 #6 事故）。
        PromptTemplate template = registry.get("resume-generate-from-jd", "v3");

        assertEquals("v3", template.version());
        assertTrue(template.content().contains("模板 v3"));
    }

    @Test
    void missingPromptIdThrowsClearException() {
        PromptTemplateNotFoundException ex = assertThrows(
                PromptTemplateNotFoundException.class,
                () -> registry.get("unknown-prompt", "v1")
        );
        assertTrue(ex.getMessage().contains("Prompt not found"));
        assertTrue(ex.getMessage().contains("unknown-prompt"));
    }

    @Test
    void missingVersionThrowsClearException() {
        PromptTemplateNotFoundException ex = assertThrows(
                PromptTemplateNotFoundException.class,
                () -> registry.get("agent-base", "v99")
        );
        assertTrue(ex.getMessage().contains("Prompt version not found"));
        assertTrue(ex.getMessage().contains("agent-base/v99"));
    }
}
