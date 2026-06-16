package com.careermate.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "careermate.agent.kernel")
public class AgentKernelProperties {

    private boolean enabled = false;
}
