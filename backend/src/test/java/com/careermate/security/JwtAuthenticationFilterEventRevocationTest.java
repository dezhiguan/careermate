package com.careermate.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.auth.events.AuthEventService;
import com.careermate.model.entity.UserEntity;
import com.careermate.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterEventRevocationTest {

    private JwtTokenProvider jwtTokenProvider;
    private UserMapper userMapper;
    private AuthEventService authEventService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        CurrentUserContext.clear();
        SecurityContextHolder.clearContext();
        jwtTokenProvider = mock(JwtTokenProvider.class);
        userMapper = mock(UserMapper.class);
        authEventService = mock(AuthEventService.class);
        filter = new JwtAuthenticationFilter(jwtTokenProvider, userMapper, new ObjectMapper(), authEventService);
    }

    @Test
    void revokedJwtReturns401BeforeUserLookup() throws Exception {
        String token = "header.payload.signature";
        Claims claims = claims("jti-1", 42L, new Date(100_000L));
        when(jwtTokenProvider.parseToken(token)).thenReturn(claims);
        when(authEventService.isJwtRevoked(any())).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(CurrentUserContext.get()).isNull();
        verify(userMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void redisFailureDuringRevocationCheckFailsClosed() throws Exception {
        String token = "header.payload.signature";
        Claims claims = claims("jti-1", 42L, new Date(100_000L));
        when(jwtTokenProvider.parseToken(token)).thenReturn(claims);
        when(authEventService.isJwtRevoked(any())).thenThrow(new RuntimeException("redis down"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void eventWebhookPathSkipsJwtAuth() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/events/session-revoked");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(jwtTokenProvider, never()).parseToken(any());
    }

    @Test
    void activeJwtContinuesToAuthenticate() throws Exception {
        String token = "header.payload.signature";
        Claims claims = claims("jti-1", 42L, new Date(100_000L));
        when(jwtTokenProvider.parseToken(token)).thenReturn(claims);
        when(authEventService.isJwtRevoked(any())).thenReturn(false);
        UserEntity user = new UserEntity();
        user.setId(42L);
        user.setUsername("amy");
        user.setRole("USER");
        user.setStatus("ACTIVE");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private Claims claims(String jti, Long userId, Date issuedAt) {
        Claims claims = mock(Claims.class);
        when(claims.getId()).thenReturn(jti);
        when(claims.get("user_id")).thenReturn(userId);
        when(claims.getIssuedAt()).thenReturn(issuedAt);
        when(claims.getSubject()).thenReturn(String.valueOf(userId));
        return claims;
    }
}
