package com.careermate.llm;

import com.careermate.common.exception.BizException;
import com.careermate.config.CareerMateDebugProperties;
import com.careermate.llm.provider.DeepSeekLlmClient;
import com.careermate.llm.provider.MockLlmClient;
import com.careermate.llm.provider.OpenAiCompatibleLlmClient;
import com.careermate.llm.provider.QwenLlmClient;
import com.careermate.llm.provider.SpringAiLlmClient;
import com.careermate.observability.LlmChatTraceRecorder;
import com.careermate.observability.LlmTracingSupport;
import com.careermate.observability.TracingLlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties({LlmProperties.class, CareerMateDebugProperties.class})
public class LlmConfig {

    @Bean
    public LlmClient llmClient(
            LlmProperties llmProperties,
            ObjectMapper objectMapper,
            LlmTracingSupport llmTracingSupport,
            LlmChatTraceRecorder llmChatTraceRecorder
    ) {
        String provider = llmProperties.getProvider() == null ? "mock" : llmProperties.getProvider().trim();
        boolean apiKeyPresent = llmProperties.getApiKey() != null && !llmProperties.getApiKey().isBlank();
        log.info("LLM client init: provider={}, model={}, endpoint={}, apiKeyConfigured={}",
                provider,
                llmProperties.getModel(),
                llmProperties.getEndpoint(),
                apiKeyPresent);
        LlmClient delegate = switch (provider) {
            case "mock" -> new MockLlmClient(llmProperties);
            // A1-5：Spring AI 支撑的统一实现，替代自研 HTTP provider
            case SpringAiLlmClient.PROVIDER_DASHSCOPE -> new SpringAiLlmClient(llmProperties, SpringAiLlmClient.PROVIDER_DASHSCOPE);
            case SpringAiLlmClient.PROVIDER_OPENAI -> new SpringAiLlmClient(llmProperties, SpringAiLlmClient.PROVIDER_OPENAI);
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
        return new TracingLlmClient(delegate, llmTracingSupport, llmChatTraceRecorder);
    }
}
