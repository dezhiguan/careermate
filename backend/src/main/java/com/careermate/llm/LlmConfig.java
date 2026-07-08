package com.careermate.llm;

import com.careermate.common.exception.BizException;
import com.careermate.config.CareerMateDebugProperties;
import com.careermate.llm.provider.MockLlmClient;
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
            // A1-5：LLM 统一走 Spring AI，自研 HTTP provider 已删除。
            // 旧 provider 名保留为别名映射到 Spring AI，兼容既有配置（qwen/deepseek 皆 DashScope 系）。
            case SpringAiLlmClient.PROVIDER_DASHSCOPE, "qwen", "deepseek" ->
                    new SpringAiLlmClient(llmProperties, SpringAiLlmClient.PROVIDER_DASHSCOPE);
            case SpringAiLlmClient.PROVIDER_OPENAI, "openai-compatible" ->
                    new SpringAiLlmClient(llmProperties, SpringAiLlmClient.PROVIDER_OPENAI);
            default -> throw new BizException(400, "未知 LLM Provider: " + provider
                    + "，支持 mock | " + SpringAiLlmClient.PROVIDER_DASHSCOPE + " | "
                    + SpringAiLlmClient.PROVIDER_OPENAI + "（qwen/deepseek/openai-compatible 为兼容别名）");
        };
        return new TracingLlmClient(delegate, llmTracingSupport, llmChatTraceRecorder);
    }
}
