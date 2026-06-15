package com.careermate.agent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AgentFrameworkProperties.class)
public class AgentFrameworkPropertiesConfig {
}
