package com.careermate.agent.cost;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LlmPricingProperties.class)
public class LlmPricingPropertiesConfig {
}
