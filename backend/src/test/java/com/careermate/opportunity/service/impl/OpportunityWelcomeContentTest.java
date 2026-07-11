package com.careermate.opportunity.service.impl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 评审 P0-2：JD 欢迎语文案模板 bug 修复（「这岗位重 相关技能」→ 通顺表达）。
 */
class OpportunityWelcomeContentTest {

    @Test
    void welcomeContentIsGrammaticalWithSkills() {
        String content = OpportunityServiceImpl.buildWelcomeContent(
                "字节跳动", "Java后端工程师", List.of("Java", "Spring", "MySQL"));

        assertTrue(content.contains("这个岗位比较看重Java、Spring、MySQL"));
        // 不得再出现残缺文案「这岗位重 」
        assertFalse(content.contains("这岗位重 "));
        assertTrue(content.contains("字节跳动 - Java后端工程师"));
    }

    @Test
    void welcomeContentFallsBackWhenSkillsEmpty() {
        String content = OpportunityServiceImpl.buildWelcomeContent("某公司", "后端", List.of());
        assertTrue(content.contains("这个岗位比较看重相关技能"));
        assertFalse(content.contains("这岗位重 "));
    }

    @Test
    void welcomeContentHandlesNullCompanyAndTitle() {
        String content = OpportunityServiceImpl.buildWelcomeContent(null, null, null);
        assertTrue(content.contains("未知公司 - 未知岗位"));
        assertTrue(content.contains("这个岗位比较看重相关技能"));
    }
}
