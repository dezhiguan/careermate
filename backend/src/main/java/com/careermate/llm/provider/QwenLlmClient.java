package com.careermate.llm.provider;

import com.careermate.llm.LlmProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

public class QwenLlmClient extends OpenAiCompatibleLlmClient {

    public QwenLlmClient(LlmProperties llmProperties, ObjectMapper objectMapper) {
        super(LlmProviderDefaults.copyWithQwenDefaults(llmProperties), objectMapper, "qwen");
    }
}
