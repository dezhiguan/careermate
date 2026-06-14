package com.careermate.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "careermate.security")
public class SecurityProperties {

    private Jwt jwt = new Jwt();
    private ClientIp clientIp = new ClientIp();

    @Data
    public static class Jwt {
        private String secret = "change-me-in-dev-only-change-me-in-dev-only";
        private Long expirationMs = 86400000L;
    }

    @Data
    public static class ClientIp {
        /** When true, prefer gateway-set X-Real-IP, else first hop in X-Forwarded-For. */
        private boolean trustProxyHeaders = false;
    }
}
