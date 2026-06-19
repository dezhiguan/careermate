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
    private AuthGateway authGateway = new AuthGateway();

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

    @Data
    public static class AuthGateway {
        private String baseUrl = "http://localhost:8090";
        private String issuer = "https://auth.careermate.cn";
        private String audience = "careermate-api";
        private String tokenEndpointAudience = "https://auth.careermate.cn/oauth/token";
        private String clientId = "careermate-backend";
        private String clientAssertionPrivateKey = "config/keys/careermate-backend.pem";
        private String clientAssertionKid = "careermate-backend";
        private String refreshCookieName = "cm_refresh";
        private String refreshCookieDomain = "";
        private String refreshCookiePath = "/api/auth";
        private int timeoutMs = 3000;
    }
}
