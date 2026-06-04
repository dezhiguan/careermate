package com.careermate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "careermate.debug")
public class CareerMateDebugProperties {

    /**
     * 是否暴露 /api/debug/llm；生产环境应设为 false。
     */
    private boolean llmApiEnabled = true;
}
