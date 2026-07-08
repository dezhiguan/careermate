package com.careermate.agent.llm;

import com.careermate.llm.LlmClient;
import com.careermate.llm.LlmProperties;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.llm.provider.SpringAiLlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 跨家评审用 LLM 路由：启用次级(deepseek)时走次级、否则回退主 LLM。
 * 供 A3 reflector / B1 critic / A5 judge 注入，达成"评审与被评不同家"防 self-bias。
 * 次级构建/调用失败静默回退主 LLM，不中断流程。
 */
@Slf4j
@Component
@EnableConfigurationProperties(SecondaryLlmProperties.class)
public class CrossFamilyLlmClient {

    private final LlmClient primary;
    private final SecondaryLlmProperties properties;
    private volatile LlmClient secondary;

    public CrossFamilyLlmClient(LlmClient primary, SecondaryLlmProperties properties) {
        this.primary = primary;
        this.properties = properties;
    }

    public boolean isCrossFamilyActive() {
        return secondaryOrNull() != null;
    }

    /** 优先次级(跨家)，不可用则主 LLM。 */
    public ChatResponse chat(ChatRequest request) {
        LlmClient s = secondaryOrNull();
        if (s != null) {
            try {
                return s.chat(request);
            } catch (Exception e) {
                log.warn("次级 LLM 调用失败，回退主 LLM: {}", e.getMessage());
            }
        }
        return primary.chat(request);
    }

    private LlmClient secondaryOrNull() {
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getApiKey())) {
            return null;
        }
        LlmClient s = secondary;
        if (s == null) {
            synchronized (this) {
                if (secondary == null) {
                    secondary = buildSecondary();
                }
                s = secondary;
            }
        }
        return s;
    }

    private LlmClient buildSecondary() {
        try {
            LlmProperties lp = new LlmProperties();
            lp.setProvider(properties.getProvider());
            lp.setApiKey(properties.getApiKey());
            lp.setEndpoint(properties.getBaseUrl());
            lp.setModel(properties.getModel());
            String provider = SpringAiLlmClient.PROVIDER_DASHSCOPE.equals(properties.getProvider())
                    ? SpringAiLlmClient.PROVIDER_DASHSCOPE : SpringAiLlmClient.PROVIDER_OPENAI;
            return new SpringAiLlmClient(lp, provider);
        } catch (Exception e) {
            log.warn("次级 LLM 构建失败，退回主 LLM: {}", e.getMessage());
            return null;
        }
    }
}
