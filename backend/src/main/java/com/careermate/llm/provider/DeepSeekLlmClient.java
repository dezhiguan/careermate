package com.careermate.llm.provider;

import com.careermate.llm.LlmProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DeepSeekLlmClient extends OpenAiCompatibleLlmClient {

    public DeepSeekLlmClient(LlmProperties llmProperties, ObjectMapper objectMapper) {
        super(ensureDefaultEndpoint(llmProperties), objectMapper, "deepseek");
    }

    private static LlmProperties ensureDefaultEndpoint(LlmProperties properties) {
        if (properties.getEndpoint() == null || properties.getEndpoint().isBlank()) {
            properties.setEndpoint("https://api.deepseek.com/v1");
        }
        if (properties.getModel() == null || properties.getModel().isBlank() || "mock-chat".equals(properties.getModel())) {
            properties.setModel("deepseek-chat");
        }
        return properties;
    }
}
