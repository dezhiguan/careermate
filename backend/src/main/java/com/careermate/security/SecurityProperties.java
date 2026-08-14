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
        private boolean refreshCookieSecure = true;
        private int timeoutMs = 3000;
        /**
         * 短信相关调用的超时。
         *
         * <p>发验证码是 careermate → 网关 → 短信服务商的三跳，服务商本身常要 1~3 秒，
         * 用通用的 3 秒封顶必然间歇性超时——线上表现为偶发 500「认证服务不可用」、重试就好。
         * 其余认证调用（校验 token、登录）仍走短超时，快速失败不受影响。
         */
        private int smsTimeoutMs = 12000;
    }
}
