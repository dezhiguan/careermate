package com.careermate.agent.memory.ltm;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(LtmProperties.class)
@EnableScheduling
public class LtmPropertiesConfig {
}
