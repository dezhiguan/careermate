package com.careermate.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.audit.AuditActionType;
import com.careermate.audit.service.AuditService;
import com.careermate.auth.gateway.AuthGatewayClient;
import com.careermate.auth.gateway.AuthGatewayCookieSupport;
import com.careermate.auth.service.AuthService;
import com.careermate.auth.dto.AuthTokenResponse;
import com.careermate.auth.dto.CurrentUserResponse;
import com.careermate.auth.dto.LoginRequest;
import com.careermate.auth.dto.RegisterRequest;
import com.careermate.auth.dto.UpdateProfileRequest;
import com.careermate.common.api.ErrorCode;
import com.careermate.common.exception.BizException;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.model.entity.UserEntity;
import com.careermate.model.entity.UserProfileEntity;
import com.careermate.security.CurrentUser;
import com.careermate.security.CurrentUserContext;
import com.careermate.security.JwtTokenProvider;
import com.careermate.security.SecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthGatewayClient authGatewayClient;
    private final AuthGatewayCookieSupport cookieSupport;
    private final AuditService auditService;

    public AuthServiceImpl(
            UserMapper userMapper,
            UserProfileMapper userProfileMapper,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            AuthGatewayClient authGatewayClient,
            AuthGatewayCookieSupport cookieSupport,
            AuditService auditService
    ) {
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authGatewayClient = authGatewayClient;
        this.cookieSupport = cookieSupport;
        this.auditService = auditService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthTokenResponse register(RegisterRequest request) {
        if (existsByUsername(request.getUsername())) {
            auditService.recordFailure(null, AuditActionType.REGISTER, "USER", null, "username duplicated");
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "用户名已存在");
        }
        if (StringUtils.hasText(request.getEmail()) && existsByEmail(request.getEmail())) {
            auditService.recordFailure(null, AuditActionType.REGISTER, "USER", null, "email duplicated");
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "邮箱已存在");
        }
        UserEntity user = new UserEntity();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmail(StringUtils.hasText(request.getEmail()) ? request.getEmail() : null);
        user.setRole("USER");
        user.setStatus("ACTIVE");
        user.setDisplayName(request.getUsername());
        userMapper.insert(user);

        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(user.getId());
        userProfileMapper.insert(profile);

        auditService.recordSuccess(
                user.getId(),
                AuditActionType.REGISTER,
                "USER",
                String.valueOf(user.getId()),
                "register username=" + user.getUsername()
        );
        return loginFromGateway(request.getUsername(), request.getPassword(), user, false);
    }

    @Override
    public AuthTokenResponse login(LoginRequest request) {
        String account = request.resolveAccount();
        if (!StringUtils.hasText(account)) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "账号不能为空");
        }
        UserEntity user = resolveUserByAccount(account);
        if (user != null) {
            assertAccountLoginable(user);
        }
        try {
            AuthTokenResponse response = loginFromGateway(account, request.getPassword(), user, false, request.isRememberMe());
            if (user != null) {
                clearLoginFailure(user);
            }
            auditService.recordSuccess(
                    user != null ? user.getId() : null,
                    AuditActionType.LOGIN,
                    "USER",
                    user != null ? String.valueOf(user.getId()) : null,
                    "login account=" + account
            );
            return response;
        } catch (BizException ex) {
            if (user != null && ex.getCode() == 401) {
                recordLoginFailure(user);
            }
            throw ex;
        }
    }

    private UserEntity resolveUserByAccount(String account) {
        if (account.contains("@")) {
            UserEntity byEmail = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                    .eq(UserEntity::getEmail, account)
                    .last("LIMIT 1"));
            if (byEmail != null) return byEmail;
        }
        if (account.matches("^1[3-9]\\d{9}$")) {
            UserEntity byPhone = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                    .eq(UserEntity::getPhone, account)
                    .last("LIMIT 1"));
            if (byPhone != null) return byPhone;
        }
        return userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, account)
                .last("LIMIT 1"));
    }

    private void assertAccountLoginable(UserEntity user) {
        String status = user.getStatus();
        if ("BANNED".equalsIgnoreCase(status)) {
            throw new BizException(ErrorCode.ACCOUNT_BANNED);
        }
        if ("CANCELLING".equalsIgnoreCase(status)) {
            throw new BizException(ErrorCode.ACCOUNT_CANCELLING);
        }
        if (!"ACTIVE".equalsIgnoreCase(status)) {
            throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), "账号状态异常，请联系客服");
        }
        if (user.getLoginLockedUntil() != null && user.getLoginLockedUntil().isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new BizException(ErrorCode.ACCOUNT_LOCKED);
        }
    }

    private void recordLoginFailure(UserEntity user) {
        int count = user.getLoginFailedCount() == null ? 0 : user.getLoginFailedCount();
        count++;
        user.setLoginFailedCount(count);
        if (count >= 5) {
            user.setLoginLockedUntil(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15));
        }
        userMapper.updateById(user);
    }

    private void clearLoginFailure(UserEntity user) {
        if (user.getLoginFailedCount() != null && user.getLoginFailedCount() > 0) {
            user.setLoginFailedCount(0);
            user.setLoginLockedUntil(null);
            userMapper.updateById(user);
        }
    }

    @Override
    public CurrentUserResponse currentUser() {
        UserEntity user = requireCurrentUserEntity();
        return toCurrentUserResponse(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CurrentUserResponse updateProfile(UpdateProfileRequest request) {
        UserEntity user = requireCurrentUserEntity();
        user.setDisplayName(request.getDisplayName().trim());
        if (request.getAvatarUrl() != null) {
            String avatarUrl = request.getAvatarUrl().trim();
            if (!avatarUrl.isEmpty() && !isValidAvatarDataUrl(avatarUrl)) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "头像格式不支持");
            }
            user.setAvatarUrl(avatarUrl.isEmpty() ? null : avatarUrl);
        }
        userMapper.updateById(user);
        auditService.recordSuccess(
                user.getId(),
                AuditActionType.PROFILE_UPDATE,
                "USER",
                String.valueOf(user.getId()),
                "update profile displayName=" + user.getDisplayName()
        );
        return toCurrentUserResponse(user);
    }

    private UserEntity requireCurrentUserEntity() {
        CurrentUser currentUser = CurrentUserContext.get();
        if (currentUser == null || !currentUser.isAuthenticated()) {
            throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        }
        UserEntity user = userMapper.selectById(currentUser.getUserId());
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        }
        return user;
    }

    private CurrentUserResponse toCurrentUserResponse(UserEntity user) {
        String displayName = StringUtils.hasText(user.getDisplayName()) ? user.getDisplayName() : user.getUsername();
        return CurrentUserResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(displayName)
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .authenticated(true)
                .build();
    }

    private boolean isValidAvatarDataUrl(String avatarUrl) {
        if (avatarUrl.length() > 600_000) {
            return false;
        }
        String lower = avatarUrl.toLowerCase();
        return lower.startsWith("data:image/jpeg;base64,")
                || lower.startsWith("data:image/jpg;base64,")
                || lower.startsWith("data:image/png;base64,")
                || lower.startsWith("data:image/webp;base64,")
                || lower.startsWith("data:image/gif;base64,");
    }

    @Override
    public void logout() {
        clearRefreshCookie();
        auditService.recordSuccess(
                currentUserId(),
                AuditActionType.LOGIN,
                "USER",
                currentUserIdStr(),
                "logout"
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logoutAll() {
        CurrentUser cu = CurrentUserContext.get();
        if (cu == null || !cu.isAuthenticated()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        UserEntity user = userMapper.selectById(cu.getUserId());
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        user.setSessionVersion(user.getSessionVersion() == null ? 1L : user.getSessionVersion() + 1);
        userMapper.updateById(user);
        clearRefreshCookie();
        auditService.recordSuccess(user.getId(), AuditActionType.LOGIN, "USER", String.valueOf(user.getId()), "logout-all");
    }

    private void clearRefreshCookie() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return;
        HttpServletResponse resp = attrs.getResponse();
        if (resp == null) return;
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("refresh_token", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        resp.addCookie(cookie);
    }

    private Long currentUserId() {
        CurrentUser cu = CurrentUserContext.get();
        return cu != null ? cu.getUserId() : null;
    }

    private String currentUserIdStr() {
        Long id = currentUserId();
        return id != null ? String.valueOf(id) : null;
    }

    private AuthTokenResponse loginFromGateway(String account, String password, UserEntity localUser, boolean isNewUser) {
        return loginFromGateway(account, password, localUser, isNewUser, false);
    }

    private AuthTokenResponse loginFromGateway(String account, String password, UserEntity localUser, boolean isNewUser, boolean rememberMe) {
        AuthGatewayClient.TokenResponse tokenResponse = authGatewayClient.loginPassword(account, password, rememberMe);
        cookieSupport.writeRefreshCookie(tokenResponse.getRefreshToken());
        long authUserId = jwtTokenProvider.getUserId(tokenResponse.getAccessToken());
        UserEntity user = localUser != null ? localUser : userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getAuthUserId, authUserId)
                .last("LIMIT 1"));
        if (user != null && !Long.valueOf(authUserId).equals(user.getAuthUserId())) {
            user.setAuthUserId(authUserId);
            userMapper.updateById(user);
        }
        String username = user != null && StringUtils.hasText(user.getUsername()) ? user.getUsername() : account;
        String role = user != null && StringUtils.hasText(user.getRole()) ? user.getRole() : jwtTokenProvider.getPlatformRole(tokenResponse.getAccessToken());
        boolean onboardingDone = user == null || user.getOnboardingCompletedAt() != null;
        return AuthTokenResponse.builder()
                .token(tokenResponse.getAccessToken())
                .tokenType(StringUtils.hasText(tokenResponse.getTokenType()) ? tokenResponse.getTokenType() : "Bearer")
                .expiresIn(tokenResponse.getExpiresIn())
                .isNewUser(isNewUser)
                .onboardingCompleted(onboardingDone)
                .user(AuthTokenResponse.UserInfo.builder()
                        .userId(user != null ? user.getId() : authUserId)
                        .username(username)
                        .role(role)
                        .build())
                .build();
    }

    private boolean existsByUsername(String username) {
        return userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, username)) > 0;
    }

    private boolean existsByEmail(String email) {
        return userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getEmail, email)) > 0;
    }
}
