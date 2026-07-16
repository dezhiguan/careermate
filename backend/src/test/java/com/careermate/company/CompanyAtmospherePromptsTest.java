package com.careermate.company;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyAtmospherePromptsTest {

    @Test
    void promptContainsCompanyAndContextAndConstraints() {
        String prompt = CompanyAtmospherePrompts.atmospherePrompt("字节跳动", "上下文情报内容");
        assertTrue(prompt.contains("字节跳动"));
        assertTrue(prompt.contains("上下文情报内容"));
        assertTrue(prompt.contains("dataAvailable"));
        assertTrue(prompt.contains("禁止编造"));
    }

    @Test
    void promptHandlesNullArgsGracefully() {
        String prompt = CompanyAtmospherePrompts.atmospherePrompt(null, null);
        assertTrue(prompt.contains("cultureTags"));
    }
}
