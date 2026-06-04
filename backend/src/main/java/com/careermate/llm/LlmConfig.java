package com.careermate.llm;

import com.careermate.common.exception.BizException;
import com.careermate.config.CareerMateDebugProperties;
import com.careermate.llm.provider.DeepSeekLlmClient;
import com.careermate.llm.provider.MockLlmClient;
import com.careermate.llm.provider.OpenAiCompatibleLlmClient;
import com.careermate.llm.provider.QwenLlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({LlmProperties.class, CareerMateDebugProperties.class})
public class LlmConfig {

    @Bean
    public LlmClient llmClient(LlmProperties llmProperties, ObjectMapper objectMapper) {
        String provider = llmProperties.getProvider() == null ? "mock" : llmProperties.getProvider().trim();
        return switch (provider) {
            case "mock" -> new MockLlmClient(llmProperties);
            case "deepseek" -> new DeepSeekLlmClient(llmProperties, objectMapper);
            case "qwen" -> new QwenLlmClient(llmProperties, objectMapper);
            case "openai-compatible" -> {
                if (llmProperties.getModel() == null || llmProperties.getModel().isBlank() || "mock-chat".equals(llmProperties.getModel())) {
                    throw new BizException(400, "LLM Model 未配置");
                }
                if (llmProperties.getEndpoint() == null || llmProperties.getEndpoint().isBlank()) {
                    throw new BizException(400, "LLM Endpoint 未配置");
                }
                yield new OpenAiCompatibleLlmClient(llmProperties, objectMapper, "openai-compatible");
            }
            default -> throw new BizException(400, "未知 LLM Provider: " + provider
                    + "，仅支持 mock | deepseek | qwen | openai-compatible");
        };
    }
}
