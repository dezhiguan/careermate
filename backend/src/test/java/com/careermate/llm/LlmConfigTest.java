package com.careermate.llm;

import com.careermate.llm.provider.DeepSeekLlmClient;
import com.careermate.llm.provider.MockLlmClient;
import com.careermate.llm.provider.OpenAiCompatibleLlmClient;
import com.careermate.llm.provider.QwenLlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class LlmConfigTest {

    private final LlmConfig llmConfig = new LlmConfig();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCreateMockClientWhenProviderIsMock() {
        LlmProperties properties = baseProperties("mock");
        assertInstanceOf(MockLlmClient.class, llmConfig.llmClient(properties, objectMapper));
    }

    @Test
    void shouldCreateDeepSeekClientWhenProviderIsDeepSeek() {
        LlmProperties properties = baseProperties("deepseek");
        assertInstanceOf(DeepSeekLlmClient.class, llmConfig.llmClient(properties, objectMapper));
    }

    @Test
    void shouldCreateQwenClientWhenProviderIsQwen() {
        LlmProperties properties = baseProperties("qwen");
        assertInstanceOf(QwenLlmClient.class, llmConfig.llmClient(properties, objectMapper));
    }

    @Test
    void shouldCreateOpenAiCompatibleClientWhenProviderIsOpenAiCompatible() {
        LlmProperties properties = baseProperties("openai-compatible");
        properties.setModel("gpt-4o-mini");
        properties.setEndpoint("https://example.openai-compatible.com/v1");
        assertInstanceOf(OpenAiCompatibleLlmClient.class, llmConfig.llmClient(properties, objectMapper));
    }

    private LlmProperties baseProperties(String provider) {
        LlmProperties properties = new LlmProperties();
        properties.setProvider(provider);
        properties.setModel("mock-chat");
        properties.setApiKey("test-key");
        properties.setEndpoint("");
        return properties;
    }
}
