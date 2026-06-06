package com.careermate.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "careermate.agent")
public class AgentProperties {

    private Long sseTimeoutMs = 300000L;
    private Integer executorCorePoolSize = 4;
    private Integer executorMaxPoolSize = 20;
    private Integer executorQueueCapacity = 200;
    private Integer maxConcurrentSessions = 100;
    private Long streamTaskTimeoutMs = 300000L;
    private Integer conversationContextMaxMessages = 10;
    private Integer conversationContextMaxChars = 6000;
}
