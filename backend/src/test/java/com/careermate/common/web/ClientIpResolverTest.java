package com.careermate.common.web;

import com.careermate.security.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {

    private SecurityProperties securityProperties;
    private ClientIpResolver resolver;

    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties();
        resolver = new ClientIpResolver(securityProperties);
    }

    @Test
    void ignoresClientSpoofedForwardedForWhenProxyTrustDisabled() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.99, 203.0.113.1");

        assertEquals("203.0.113.10", resolver.resolve(request));
    }

    @Test
    void prefersRealIpWhenProxyTrustEnabled() {
        securityProperties.getClientIp().setTrustProxyHeaders(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", "203.0.113.20");
        request.addHeader("X-Forwarded-For", "198.51.100.99");

        assertEquals("203.0.113.20", resolver.resolve(request));
    }

    @Test
    void usesFirstForwardedHopWhenRealIpMissingAndProxyTrustEnabled() {
        securityProperties.getClientIp().setTrustProxyHeaders(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.30, 198.51.100.99");

        assertEquals("203.0.113.30", resolver.resolve(request));
    }
}
