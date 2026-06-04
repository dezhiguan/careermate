package com.careermate.llm;

import com.careermate.llm.provider.LlmProviderDefaults;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LlmProviderDefaultsTest {

    @Test
    void userFacingHttpErrorDoesNotExposeSecrets() {
        assertEquals("LLM 认证失败，请检查 API Key 配置", LlmProviderDefaults.userFacingHttpError(401));
        assertEquals("LLM 调用失败", LlmProviderDefaults.userFacingHttpError(400));
    }

    @Test
    void sanitizeForLogRedactsBearerAndSkPatterns() {
        String raw = "Authorization Bearer sk-testsecret12345 failed";
        String sanitized = LlmProviderDefaults.sanitizeForLog(raw);
        assertFalse(sanitized.contains("sk-testsecret12345"));
        assertFalse(sanitized.toLowerCase().contains("bearer sk"));
    }

    @Test
    void safeStreamErrorMessageRedactsEmbeddedSecrets() {
        String message = LlmProviderDefaults.safeStreamErrorMessage(
                new RuntimeException("invalid key sk-abcdef1234567890")
        );
        assertEquals("模型服务异常，请稍后重试", message);
    }
}
