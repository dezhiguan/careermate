package com.careermate.agent.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 跨家次级 LLM 配置（reflector/critic/judge 用，与主 provider 不同家防 self-bias）。默认 deepseek。
 */
@Data
@ConfigurationProperties(prefix = "careermate.agent.secondary-llm")
public class SecondaryLlmProperties {

    /** 是否启用次级 LLM；关闭时相关组件回退主 LLM。 */
    private boolean enabled = false;
    /** provider（deepseek 走 OpenAI 兼容）。 */
    private String provider = "spring-ai-openai";
    /** OpenAI 兼容端点。 */
    private String baseUrl = "https://api.deepseek.com/v1";
    /** 模型。 */
    private String model = "deepseek-v4-flash";
    /** api-key（留空复用 DEEPSEEK_API_KEY）。 */
    private String apiKey = "";
}
