package com.careermate.auth.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.careermate.security.SecurityProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AuthGatewayCookieSupportTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void writeRefreshCookieUsesSecureByDefault() {
        MockHttpServletResponse response = bindResponse();
        AuthGatewayCookieSupport support = new AuthGatewayCookieSupport(new SecurityProperties());

        support.writeRefreshCookie("refresh-token");

        assertThat(response.getHeader("Set-Cookie"))
                .contains("cm_refresh=refresh-token")
                .contains("HttpOnly")
                .contains("Secure");
    }

    @Test
    void writeRefreshCookieCanDisableSecureForLocalDevelopment() {
        MockHttpServletResponse response = bindResponse();
        SecurityProperties properties = new SecurityProperties();
        properties.getAuthGateway().setRefreshCookieSecure(false);
        AuthGatewayCookieSupport support = new AuthGatewayCookieSupport(properties);

        support.writeRefreshCookie("refresh-token");

        assertThat(response.getHeader("Set-Cookie"))
                .contains("cm_refresh=refresh-token")
                .doesNotContain("Secure");
    }

    private MockHttpServletResponse bindResponse() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest(), response));
        return response;
    }
}
