package com.careermate.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "careermate.agent.framework")
public class AgentFrameworkProperties {

    private boolean enabled = false;
    private String provider = "spring-ai-openai";
    private String model;
    private String baseUrl;
    private String apiKey;
    private Long timeoutMs = 60000L;
}
