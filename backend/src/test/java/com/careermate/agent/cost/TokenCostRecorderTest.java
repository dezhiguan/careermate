package com.careermate.agent.cost;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenCostRecorderTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();

    private LlmPricingProperties pricingWith(String model, double in, double out) {
        LlmPricingProperties p = new LlmPricingProperties();
        p.getModels().put(model, new LlmPricingProperties.ModelPrice(in, out));
        return p;
    }

    @Test
    void computesCostFromPricingTableAndRecordsMetrics() {
        LlmPricingProperties pricing = pricingWith("qwen-plus", 0.001, 0.002);
        TokenCostRecorder recorder = new TokenCostRecorder(registry, pricing);

        double cost = recorder.record(7L, "qwen", "qwen-plus", 1000, 2000, false);

        // 1000/1000*0.001 + 2000/1000*0.002 = 0.001 + 0.004 = 0.005
        assertThat(cost).isEqualTo(0.005);
        assertThat(registry.get("agent.token.input").counter().count()).isEqualTo(1000);
        assertThat(registry.get("agent.token.output").counter().count()).isEqualTo(2000);
        assertThat(registry.get("agent.token.cost").counter().count()).isEqualTo(0.005);
    }

    @Test
    void unknownModelUsesDefaultPrice() {
        LlmPricingProperties pricing = new LlmPricingProperties(); // default 0.001/0.002
        TokenCostRecorder recorder = new TokenCostRecorder(registry, pricing);

        double cost = recorder.record(7L, "qwen", "unknown-model", 1000, 1000, true);

        assertThat(cost).isEqualTo(0.003);
    }

    @Test
    void estimateTokens_roughlyCharsOverFour() {
        assertThat(TokenCostRecorder.estimateTokens(null)).isZero();
        assertThat(TokenCostRecorder.estimateTokens("")).isZero();
        assertThat(TokenCostRecorder.estimateTokens("abcd")).isEqualTo(1);
        assertThat(TokenCostRecorder.estimateTokens("a".repeat(400))).isEqualTo(100);
    }

    @Test
    void nullTagsHandledGracefully() {
        TokenCostRecorder recorder = new TokenCostRecorder(registry, new LlmPricingProperties());
        double cost = recorder.record(null, null, null, 100, 100, true);
        assertThat(cost).isGreaterThan(0);
        assertThat(registry.get("agent.token.cost").counter().count()).isGreaterThan(0);
    }
}
