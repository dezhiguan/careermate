package com.careermate.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "careermate.security")
public class SecurityProperties {

    private String mode = "single-user";
    private Jwt jwt = new Jwt();
    private SingleUser singleUser = new SingleUser();

    @Data
    public static class Jwt {
        private String secret = "change-me-in-dev-only-change-me-in-dev-only";
        private Long expirationMs = 86400000L;
    }

    @Data
    public static class SingleUser {
        private Long userId = 1L;
        private String username = "local-user";
    }
}
