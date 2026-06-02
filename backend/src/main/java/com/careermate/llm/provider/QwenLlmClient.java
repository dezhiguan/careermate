package com.careermate.llm.provider;

import com.careermate.llm.LlmProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

public class QwenLlmClient extends OpenAiCompatibleLlmClient {

    public QwenLlmClient(LlmProperties llmProperties, ObjectMapper objectMapper) {
        super(ensureDefaults(llmProperties), objectMapper, "qwen");
    }

    private static LlmProperties ensureDefaults(LlmProperties properties) {
        if (properties.getEndpoint() == null || properties.getEndpoint().isBlank()) {
            properties.setEndpoint("https://dashscope.aliyuncs.com/compatible-mode/v1");
        }
        if (properties.getModel() == null || properties.getModel().isBlank() || "mock-chat".equals(properties.getModel())) {
            properties.setModel("qwen-plus");
        }
        return properties;
    }
}
