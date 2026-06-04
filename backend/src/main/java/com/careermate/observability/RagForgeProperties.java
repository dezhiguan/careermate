package com.careermate.observability;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "careermate.ragforge")
public class RagForgeProperties {

    private boolean enabled = false;
    private String url = "http://localhost:8080";
    private String apiKey = "";
    private int timeoutMs = 5000;
    private String jdKbId = "";
}
