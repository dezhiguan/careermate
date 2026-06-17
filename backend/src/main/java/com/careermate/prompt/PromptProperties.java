package com.careermate.prompt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "careermate.prompt")
public class PromptProperties {

    private Map<String, String> activeVersions = new LinkedHashMap<>();
}
