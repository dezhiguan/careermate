package com.careermate.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "careermate.llm")
public class LlmProperties {

    private String provider = "mock";
    private String model = "mock-chat";
    private String apiKey;
    private String endpoint;
    private Long timeoutMs = 60000L;
    private Integer maxTokens = 4096;
    private Double temperature = 0.7D;
}
