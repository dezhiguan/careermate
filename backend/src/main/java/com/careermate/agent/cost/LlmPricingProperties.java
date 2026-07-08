package com.careermate.agent.cost;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A5：LLM 单价表（每 1k token 价格，单位由部署方约定，默认按 CNY）。
 * key = model 名（小写），未命中用 {@link #defaultPrice}。
 */
@Data
@ConfigurationProperties(prefix = "careermate.agent.pricing")
public class LlmPricingProperties {

    private Map<String, ModelPrice> models = new LinkedHashMap<>();
    private ModelPrice defaultPrice = new ModelPrice(0.001, 0.002);

    public ModelPrice priceFor(String model) {
        if (model == null) {
            return defaultPrice;
        }
        ModelPrice p = models.get(model.trim().toLowerCase());
        return p != null ? p : defaultPrice;
    }

    @Data
    public static class ModelPrice {
        /** 每 1k 输入 token 单价。 */
        private double input;
        /** 每 1k 输出 token 单价。 */
        private double output;

        public ModelPrice() {
        }

        public ModelPrice(double input, double output) {
            this.input = input;
            this.output = output;
        }
    }
}
