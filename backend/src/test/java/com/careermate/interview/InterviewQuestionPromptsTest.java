package com.careermate.interview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewQuestionPromptsTest {

    @Test
    void promptContainsAllContextsAndTagRules() {
        String prompt = InterviewQuestionPrompts.jdAwarePrompt(
                "字节-后端", "JD正文内容", "简历摘要内容", "题库参考内容");
        assertTrue(prompt.contains("字节-后端"));
        assertTrue(prompt.contains("JD正文内容"));
        assertTrue(prompt.contains("简历摘要内容"));
        assertTrue(prompt.contains("题库参考内容"));
        assertTrue(prompt.contains("JD_FOCUSED"));
        assertTrue(prompt.contains("WEAK_POINT"));
    }

    @Test
    void promptHandlesNullArgs() {
        String prompt = InterviewQuestionPrompts.jdAwarePrompt(null, null, null, null);
        assertTrue(prompt.contains("questions"));
    }
}
