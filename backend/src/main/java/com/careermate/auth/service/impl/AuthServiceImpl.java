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
    private final com.careermate.auth.session.LoginSessionRecorder loginSessionRecorder;
    private final com.careermate.auth.events.AuthEventService authEventService;

    public AuthServiceImpl(
            UserMapper userMapper,
            UserProfileMapper userProfileMapper,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            AuthGatewayClient authGatewayClient,
            AuthGatewayCookieSupport cookieSupport,
            AuditService auditService,
            com.careermate.auth.session.LoginSessionRecorder loginSessionRecorder,
            com.careermate.auth.events.AuthEventService authEventService
    ) {
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authGatewayClient = authGatewayClient;
        this.cookieSupport = cookieSupport;
        this.auditService = auditService;
        this.loginSessionRecorder = loginSessionRecorder;
        this.authEventService = authEventService;
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
            // captcha/challengeId 透传给 auth-gateway（权威图形验证码，与 RAGForge 一致）；
            // 网关要求验证码时会抛 CaptchaRequiredException（非 BizException），直接向上传播携带图片
            AuthTokenResponse response = loginFromGateway(account, request.getPassword(), user, false,
                    request.isRememberMe(), request.getCaptcha(), request.getCaptchaChallengeId());
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
                RuntimeException enriched = buildLoginFailure(user);
                if (enriched != null) {
                    throw enriched;
                }
            }
            throw ex;
        }
    }

    // 图形验证码 + 锁定由 auth-gateway 统一负责（阈值 5，与 RAGForge 一致）。
    // CareerMate 本地仅保留失败计数用于"还可以尝试 N 次"的友好提示，不再本地硬锁定，
    // 以免抢在网关图形验证码之前把用户锁死。
    private static final int GATEWAY_CAPTCHA_THRESHOLD = 5;

    /**
     * 根据当前失败次数生成友好的登录失败提示（剩余次数）。
     * 返回 null 表示无需增强（第 1 次失败或已达网关验证码阶段），由调用方沿用网关原始异常与文案。
     */
    private RuntimeException buildLoginFailure(UserEntity user) {
        int count = user.getLoginFailedCount() == null ? 0 : user.getLoginFailedCount();
        // 第 2~4 次失败给出剩余次数提醒；第 5 次起由网关返回图形验证码（CaptchaRequiredException 已在网关客户端抛出）
        if (count >= 2 && count < GATEWAY_CAPTCHA_THRESHOLD) {
            int remaining = GATEWAY_CAPTCHA_THRESHOLD - count;
            return new BizException(ErrorCode.UNAUTHORIZED.getCode(),
                    "密码错误，还可以尝试 " + remaining + " 次，之后需完成图形验证码");
        }
        return null;
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
        // CANCELLING（注销冷静期）允许登录：用户需登录后才能在账号设置里"撤销注销"。
        // 前端依据 /me 返回的 status=CANCELLING 展示撤销入口。
        if (!"ACTIVE".equalsIgnoreCase(status) && !"CANCELLING".equalsIgnoreCase(status)) {
            throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), "账号状态异常，请联系客服");
        }
        // 锁定/图形验证码交由 auth-gateway 统一负责，本地不再硬锁定（避免抢在网关验证码之前锁死账号）。
        // 兼容历史遗留的本地锁：若之前已写入未过期的 loginLockedUntil，仍尊重之。
        if (user.getLoginLockedUntil() != null && user.getLoginLockedUntil().isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new BizException(ErrorCode.ACCOUNT_LOCKED);
        }
    }

    private void recordLoginFailure(UserEntity user) {
        // 仅累计失败次数用于"还可以尝试 N 次"提示；不再本地设置锁定时间（锁定/验证码归 auth-gateway）
        int count = user.getLoginFailedCount() == null ? 0 : user.getLoginFailedCount();
        count++;
        user.setLoginFailedCount(count);
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
                .phone(user.getPhone())
                .email(user.getEmail())
                .emailVerified(user.getEmailVerified())
                .hasPassword(StringUtils.hasText(user.getPasswordHash()))
                .status(user.getStatus())
                .deletionScheduledAt(user.getDeletionScheduledAt() == null ? null
                        : user.getDeletionScheduledAt().toInstant().toString())
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
        // 单设备退出：精确吊销当前 access token 的 jti，使旧 token 立即失效（此前仅清 cookie，token 仍有效至过期）
        CurrentUser cu = CurrentUserContext.get();
        if (cu != null && StringUtils.hasText(cu.getJti())) {
            // TTL 用默认值即可覆盖 15 分钟 access token 生命周期
            authEventService.revokeJti(cu.getJti(), null);
            deleteSessionQuietly(cu.getJti());
        }
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
        // 退出全部设备：用户级吊销 —— 该用户当前时刻之前签发的所有 access token 全部失效
        if (StringUtils.hasText(cu.getAuthUserKey())) {
            authEventService.revokeUserAfter(cu.getAuthUserKey(), java.time.Instant.now().getEpochSecond());
        }
        deleteAllSessionsQuietly(user.getId());
        clearRefreshCookie();
        auditService.recordSuccess(user.getId(), AuditActionType.LOGIN, "USER", String.valueOf(user.getId()), "logout-all");
    }

    private void deleteSessionQuietly(String jti) {
        try {
            loginSessionRecorder.deleteById(jti);
        } catch (RuntimeException ignored) {
            // best-effort
        }
    }

    private void deleteAllSessionsQuietly(Long localUserId) {
        try {
            loginSessionRecorder.deleteAllForUser(localUserId);
        } catch (RuntimeException ignored) {
            // best-effort
        }
    }

    private HttpServletRequest currentHttpRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
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
        return loginFromGateway(account, password, localUser, isNewUser, false, null, null);
    }

    private AuthTokenResponse loginFromGateway(String account, String password, UserEntity localUser, boolean isNewUser, boolean rememberMe) {
        return loginFromGateway(account, password, localUser, isNewUser, rememberMe, null, null);
    }

    private AuthTokenResponse loginFromGateway(String account, String password, UserEntity localUser, boolean isNewUser,
                                               boolean rememberMe, String captcha, String captchaChallengeId) {
        // 仅在带图形验证码时走 5 参重载；否则沿用 2/3 参重载，保持与既有调用/测试桩兼容
        boolean hasCaptcha = (captcha != null && !captcha.isBlank())
                || (captchaChallengeId != null && !captchaChallengeId.isBlank());
        AuthGatewayClient.TokenResponse tokenResponse;
        if (hasCaptcha) {
            tokenResponse = authGatewayClient.loginPassword(account, password, rememberMe, captcha, captchaChallengeId);
        } else if (rememberMe) {
            tokenResponse = authGatewayClient.loginPassword(account, password, true);
        } else {
            tokenResponse = authGatewayClient.loginPassword(account, password);
        }
        cookieSupport.writeRefreshCookie(tokenResponse.getRefreshToken(), rememberMe);
        long authUserId = jwtTokenProvider.getUserId(tokenResponse.getAccessToken());
        UserEntity user = localUser != null ? localUser : userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getAuthUserId, authUserId)
                .last("LIMIT 1"));
        if (user != null && !Long.valueOf(authUserId).equals(user.getAuthUserId())) {
            user.setAuthUserId(authUserId);
            userMapper.updateById(user);
        }
        if (user != null) {
            loginSessionRecorder.record(user.getId(), tokenResponse.getAccessToken(), rememberMe, currentHttpRequest());
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
