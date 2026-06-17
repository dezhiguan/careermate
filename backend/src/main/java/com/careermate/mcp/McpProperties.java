package com.careermate.mcp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "careermate.mcp")
public class McpProperties {

    private boolean enabled = false;
}
