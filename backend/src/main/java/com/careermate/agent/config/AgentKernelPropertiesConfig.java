package com.careermate.agent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AgentKernelProperties.class)
public class AgentKernelPropertiesConfig {
}
