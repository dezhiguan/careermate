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

    public void writeRefreshCookie(String refreshToken) {
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
                .maxAge(Duration.ofDays(7));
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
