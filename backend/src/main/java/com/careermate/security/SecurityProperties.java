package com.careermate.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "careermate.security")
public class SecurityProperties {

    private Jwt jwt = new Jwt();

    @Data
    public static class Jwt {
        private String secret = "change-me-in-dev-only-change-me-in-dev-only";
        private Long expirationMs = 86400000L;
    }
}
