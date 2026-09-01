package com.careermate.security;

import com.careermate.common.api.ApiResponse;
import com.careermate.common.api.ErrorCode;
import com.careermate.auth.events.AuthEventService;
import com.careermate.auth.events.AuthJwtToken;
import com.careermate.auth.identity.LocalUserMirror;
import com.careermate.model.entity.UserEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import com.careermate.observability.MdcKeys;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final LocalUserMirror localUserMirror;
    private final ObjectMapper objectMapper;
    private final AuthEventService authEventService;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            LocalUserMirror localUserMirror,
            ObjectMapper objectMapper,
            AuthEventService authEventService
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.localUserMirror = localUserMirror;
        this.objectMapper = objectMapper;
        this.authEventService = authEventService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            if (!applyJwtAuth(request, response)) {
                return;
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MdcKeys.USER_ID);
            CurrentUserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private boolean applyJwtAuth(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getRequestURI();
        if (isAnonymousPath(path)) {
            return true;
        }
        if (!path.startsWith("/api/")) {
            return true;
        }
        String bearerToken = extractBearerToken(request);
        if (!StringUtils.hasText(bearerToken)) {
            writeUnauthorized(response, ErrorCode.UNAUTHORIZED.getMessage());
            return false;
        }
        Claims claims;
        try {
            claims = jwtTokenProvider.parseToken(bearerToken);
            if (authEventService.isJwtRevoked(new AuthJwtToken(
                    claims.getId(),
                    userKey(claims),
                    issuedAtEpochSeconds(claims)
            ))) {
                writeUnauthorized(response, ErrorCode.UNAUTHORIZED.getMessage());
                return false;
            }
        } catch (RuntimeException ex) {
            writeUnauthorized(response, ErrorCode.UNAUTHORIZED.getMessage());
            return false;
        }
        return authenticateUserFromClaims(claims, response);
    }

    private boolean authenticateUserFromClaims(Claims claims, HttpServletResponse response) throws IOException {
        Long userId = getUserId(claims);
        UserEntity user = localUserMirror.findByAuthUserId(userId);
        if (user == null) {
            writeUnauthorized(response, "用户不存在");
            return false;
        }
        // ACTIVE 正常；CANCELLING（注销冷静期）也需能鉴权 —— 否则用户无法调用撤销注销接口，
        // 会被永久锁死。其余状态（BANNED 等）一律拒绝。
        String status = user.getStatus();
        boolean allowed = "ACTIVE".equalsIgnoreCase(status) || "CANCELLING".equalsIgnoreCase(status);
        if (!allowed) {
            writeUnauthorized(response, "用户已被禁用");
            return false;
        }
        setSecurityContext(
                CurrentUser.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .role(user.getRole())
                        .authenticated(true)
                        .jti(claims.getId())
                        .authUserKey(userKey(claims))
                        .issuedAtEpochSeconds(issuedAtEpochSeconds(claims))
                        .build()
        );
        return true;
    }

    private void setSecurityContext(CurrentUser currentUser) {
        CurrentUserContext.set(currentUser);
        if (currentUser.getUserId() != null) {
            MDC.put(MdcKeys.USER_ID, String.valueOf(currentUser.getUserId()));
        }
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                currentUser.getUsername(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + currentUser.getRole()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring(7);
    }

    private boolean isAnonymousPath(String path) {
        return "/api/health".equals(path)
                || "/api/auth/register".equals(path)
                || "/api/auth/login".equals(path)
                || "/api/auth/sms/send".equals(path)
                || "/api/auth/captcha".equals(path)
                || "/api/auth/refresh".equals(path)
                || "/api/auth/mobile/login".equals(path)
                || "/api/auth/password-reset/sms/send".equals(path)
                || "/api/auth/password-reset/confirm".equals(path)
                || "/api/.well-known/careermate-backend-jwks.json".equals(path)
                || path.startsWith("/api/v1/events/")
                || path.startsWith("/api/events/")
                || "/actuator/health".equals(path);
    }

    private Long getUserId(Claims claims) {
        Object userId = claims.get("user_id");
        if (userId == null) {
            userId = claims.get("userId");
        }
        if (userId instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(userId));
    }

    private String userKey(Claims claims) {
        Object userId = claims.get("user_id");
        if (userId == null) {
            userId = claims.get("userId");
        }
        return userId == null ? claims.getSubject() : String.valueOf(userId);
    }

    private Long issuedAtEpochSeconds(Claims claims) {
        if (claims.getIssuedAt() == null) {
            return null;
        }
        return claims.getIssuedAt().toInstant().getEpochSecond();
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(
                ErrorCode.UNAUTHORIZED.getCode(),
                message
        )));
    }
}
