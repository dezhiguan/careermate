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
            String mode = securityProperties.getMode();
            if (MODE_SINGLE_USER.equals(mode)) {
                applySingleUserAuth(request);
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
            CurrentUserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void applySingleUserAuth(HttpServletRequest request) {
        if (isAnonymousPath(request.getRequestURI())) {
            return;
        }
        Long userId = securityProperties.getSingleUser().getUserId();
        String username = securityProperties.getSingleUser().getUsername();
        CurrentUser currentUser = CurrentUser.builder()
                .userId(userId)
                .username(username)
                .role("USER")
                .authenticated(true)
                .build();
        CurrentUserContext.set(currentUser);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                username,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private boolean applyJwtAuth(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getRequestURI();
        if (isAnonymousPath(path)) {
            return true;
        }
        if (!path.startsWith("/api/")) {
            return true;
        }
        String authHeader = request.getHeader("Authorization");
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return true;
        }
        String token = authHeader.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            writeUnauthorized(response, ErrorCode.UNAUTHORIZED.getMessage());
            return false;
        }
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
        CurrentUser currentUser = CurrentUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .authenticated(true)
                .build();
        CurrentUserContext.set(currentUser);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user.getUsername(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return true;
    }

    private boolean isAnonymousPath(String path) {
        return "/api/health".equals(path)
                || "/api/auth/register".equals(path)
                || "/api/auth/login".equals(path)
                || "/actuator/health".equals(path);
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
