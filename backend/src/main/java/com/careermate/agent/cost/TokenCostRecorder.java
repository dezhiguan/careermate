package com.careermate.agent.cost;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * A5：Token 成本计量——落 Micrometer metric（非仅日志），按 provider/model/user 打标。
 *
 * <p>指标：{@code agent.token.input} / {@code agent.token.output}（token 数）、
 * {@code agent.token.cost}（按单价表折算成本）。计量失败不影响对话。
 */
@Slf4j
@Component
public class TokenCostRecorder {

    private final MeterRegistry meterRegistry;
    private final LlmPricingProperties pricing;

    public TokenCostRecorder(MeterRegistry meterRegistry, LlmPricingProperties pricing) {
        this.meterRegistry = meterRegistry;
        this.pricing = pricing;
    }

    /**
     * @param estimated token 是否为估算值（provider 未返回用量时按字符估算）
     * @return 本次折算成本
     */
    public double record(Long userId, String provider, String model,
                         int inputTokens, int outputTokens, boolean estimated) {
        LlmPricingProperties.ModelPrice price = pricing.priceFor(model);
        double cost = inputTokens / 1000.0 * price.getInput() + outputTokens / 1000.0 * price.getOutput();
        try {
            Tags tags = Tags.of(
                    "provider", provider == null ? "unknown" : provider,
                    "model", model == null ? "unknown" : model,
                    "user", userId == null ? "anon" : String.valueOf(userId),
                    "estimated", String.valueOf(estimated));
            meterRegistry.counter("agent.token.input", tags).increment(inputTokens);
            meterRegistry.counter("agent.token.output", tags).increment(outputTokens);
            meterRegistry.counter("agent.token.cost", tags).increment(cost);
        } catch (Exception e) {
            log.warn("token 成本计量失败（不影响对话）: {}", e.getMessage());
        }
        return cost;
    }

    /** 无 provider 用量时，按字符粗估 token（约 4 字符/token）。 */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }
}
