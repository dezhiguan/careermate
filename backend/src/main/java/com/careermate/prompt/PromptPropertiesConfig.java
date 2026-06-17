package com.careermate.prompt;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PromptProperties.class)
public class PromptPropertiesConfig {
}
