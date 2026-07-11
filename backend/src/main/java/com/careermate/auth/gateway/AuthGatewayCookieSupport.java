package com.careermate.auth.gateway;

import com.careermate.security.SecurityProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

@Component
public class AuthGatewayCookieSupport {

    private final SecurityProperties.AuthGateway properties;

    public AuthGatewayCookieSupport(SecurityProperties securityProperties) {
        this.properties = securityProperties.getAuthGateway();
    }

    private static final Duration DEFAULT_TTL = Duration.ofDays(7);
    private static final Duration REMEMBER_TTL = Duration.ofDays(30);

    public void writeRefreshCookie(String refreshToken) {
        writeRefreshCookie(refreshToken, false);
    }

    /**
     * 写 refresh cookie。rememberMe=true 时 cookie 有效期 30 天（与网关 remember 的 refresh token TTL 对齐），
     * 否则 7 天。此前写死 7 天，导致"记住我30天"在浏览器侧被截断。
     */
    public void writeRefreshCookie(String refreshToken, boolean rememberMe) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }
        HttpServletResponse response = currentResponse();
        if (response == null) {
            return;
        }
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(properties.getRefreshCookieName(), refreshToken)
                .httpOnly(true)
                .secure(properties.isRefreshCookieSecure())
                .sameSite("Lax")
                .path(properties.getRefreshCookiePath())
                .maxAge(rememberMe ? REMEMBER_TTL : DEFAULT_TTL);
        if (StringUtils.hasText(properties.getRefreshCookieDomain())) {
            builder.domain(properties.getRefreshCookieDomain());
        }
        response.addHeader("Set-Cookie", builder.build().toString());
    }

    private HttpServletResponse currentResponse() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getResponse();
        }
        return null;
    }
}
