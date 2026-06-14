package com.careermate.common.web;

import com.careermate.security.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ClientIpResolver {

    private final SecurityProperties securityProperties;

    public ClientIpResolver(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        if (securityProperties.getClientIp().isTrustProxyHeaders()) {
            String realIp = firstNonBlankHeader(request, "X-Real-IP");
            if (StringUtils.hasText(realIp)) {
                return realIp.trim();
            }
            String forwarded = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                return forwarded.split(",")[0].trim();
            }
        }
        String remoteAddr = request.getRemoteAddr();
        return StringUtils.hasText(remoteAddr) ? remoteAddr : "unknown";
    }

    private String firstNonBlankHeader(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        return StringUtils.hasText(value) ? value : null;
    }
}
