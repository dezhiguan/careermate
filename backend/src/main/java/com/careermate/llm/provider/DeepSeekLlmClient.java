package com.careermate.llm.provider;

import com.careermate.llm.LlmProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DeepSeekLlmClient extends OpenAiCompatibleLlmClient {

    public DeepSeekLlmClient(LlmProperties llmProperties, ObjectMapper objectMapper) {
        super(LlmProviderDefaults.copyWithDeepSeekDefaults(llmProperties), objectMapper, "deepseek");
    }
}
