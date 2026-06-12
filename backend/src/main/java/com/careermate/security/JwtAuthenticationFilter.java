package com.careermate.security;

import com.careermate.common.api.ApiResponse;
import com.careermate.common.api.ErrorCode;
import com.careermate.mapper.UserMapper;
import com.careermate.model.entity.UserEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final String MODE_SINGLE_USER = "single-user";
    private static final String MODE_JWT = "jwt";

    private final SecurityProperties securityProperties;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(
            SecurityProperties securityProperties,
            JwtTokenProvider jwtTokenProvider,
            UserMapper userMapper,
            ObjectMapper objectMapper
    ) {
        this.securityProperties = securityProperties;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            if (securityProperties.isDevSkipAuth() && request.getRequestURI().startsWith("/api/")) {
                if (!isAnonymousPath(request.getRequestURI())) {
                    applyConfiguredSingleUser();
                }
                filterChain.doFilter(request, response);
                return;
            }

            String mode = securityProperties.getMode();
            if (MODE_SINGLE_USER.equals(mode)) {
                if (!applySingleUserAuth(request, response)) {
                    return;
                }
                filterChain.doFilter(request, response);
                return;
            }
            if (MODE_JWT.equals(mode)) {
                if (!applyJwtAuth(request, response)) {
                    return;
                }
                filterChain.doFilter(request, response);
                return;
            }
            writeUnauthorized(response, "Unsupported SECURITY_MODE");
        } finally {
            MDC.remove(MdcKeys.USER_ID);
            CurrentUserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * single-user 模式：无 Token 时回落为 local-user；携带有效 JWT 时以 Token 对应用户为准（保证登录态刷新）。
     */
    private boolean applySingleUserAuth(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (isAnonymousPath(request.getRequestURI())) {
            return true;
        }
        String bearerToken = extractBearerToken(request);
        if (StringUtils.hasText(bearerToken)) {
            if (!jwtTokenProvider.validateToken(bearerToken)) {
                applyConfiguredSingleUser();
                return true;
            }
            return authenticateUserFromToken(bearerToken, response);
        }
        applyConfiguredSingleUser();
        return true;
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
        if (!jwtTokenProvider.validateToken(bearerToken)) {
            writeUnauthorized(response, ErrorCode.UNAUTHORIZED.getMessage());
            return false;
        }
        return authenticateUserFromToken(bearerToken, response);
    }

    private void applyConfiguredSingleUser() {
        Long userId = securityProperties.getSingleUser().getUserId();
        String username = securityProperties.getSingleUser().getUsername();
        setSecurityContext(
                CurrentUser.builder()
                        .userId(userId)
                        .username(username)
                        .role("USER")
                        .authenticated(true)
                        .build()
        );
    }

    private boolean authenticateUserFromToken(String token, HttpServletResponse response) throws IOException {
        Long userId = jwtTokenProvider.getUserId(token);
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getId, userId)
                .last("LIMIT 1"));
        if (user == null) {
            writeUnauthorized(response, "用户不存在");
            return false;
        }
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            writeUnauthorized(response, "用户已被禁用");
            return false;
        }
        setSecurityContext(
                CurrentUser.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .role(user.getRole())
                        .authenticated(true)
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
                || "/actuator/health".equals(path)
                || isResumeVersionPdfExport(path);
    }

    private static boolean isResumeVersionPdfExport(String path) {
        return path != null && path.matches("/api/resume-version/[^/]+/export/pdf");
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
