package com.careermate.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Slf4j
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

    /** 与 resolveUserByAccount 共用：判断 account 是否手机号形态。 */
    private static final String MAINLAND_PHONE_PATTERN = "^1[3-9]\\d{9}$";

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
        if (account.matches(MAINLAND_PHONE_PATTERN)) {
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
        // 列级更新：整行回写会把 auth_user_id 等共享身份列一起带上，撞唯一索引就把登录打成 500
        updateColumns(user, new LambdaUpdateWrapper<UserEntity>()
                .eq(UserEntity::getId, user.getId())
                .set(UserEntity::getLoginFailedCount, count));
    }

    private void clearLoginFailure(UserEntity user) {
        if (user.getLoginFailedCount() != null && user.getLoginFailedCount() > 0) {
            user.setLoginFailedCount(0);
            user.setLoginLockedUntil(null);
            updateColumns(user, new LambdaUpdateWrapper<UserEntity>()
                    .eq(UserEntity::getId, user.getId())
                    .set(UserEntity::getLoginFailedCount, 0)
                    .set(UserEntity::getLoginLockedUntil, null));
        }
    }

    /** 登录失败计数一类的附带写入：只碰指定列，且写不进去也不阻断登录。 */
    private void updateColumns(UserEntity user, LambdaUpdateWrapper<UserEntity> wrapper) {
        try {
            userMapper.update(null, wrapper);
        } catch (Exception ex) {
            log.warn("更新用户登录计数失败（不影响登录） userId={}: {}", user.getId(), ex.toString());
        }
    }

    /**
     * 把网关身份回填到本地行。用只带 id + auth_user_id 的列级更新，且**写库成功后才同步内存对象**——
     * 先改内存再写库，一旦写失败，脏值会被后续 clearLoginFailure 的整行回写再次带去撞唯一索引。
     *
     * @return false 表示该 auth_user_id 已被本地另一行占用（重复账号行），调用方应放弃后续同步
     */
    private boolean backfillAuthUserId(UserEntity user, long authUserId) {
        try {
            userMapper.update(null, new LambdaUpdateWrapper<UserEntity>()
                    .eq(UserEntity::getId, user.getId())
                    .set(UserEntity::getAuthUserId, authUserId));
            user.setAuthUserId(authUserId);
            return true;
        } catch (DuplicateKeyException ex) {
            log.error("本地 users 存在重复账号行：auth_user_id={} 已被另一行占用，当前行 userId={} 无法认领网关身份，"
                    + "登录已放行但两行数据是分裂的，需人工合并", authUserId, user.getId());
            return false;
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
        // 走到这里认证已经成功、refresh cookie 已下发。以下只是本地 users 镜像的解析与同步，
        // 任何失败都不得让一次已成功的登录返回 5xx——否则用户看到「系统异常」，实际却已处于半登录状态。
        // 身份的唯一权威是网关的 auth_user_id：JwtAuthenticationFilter 每次请求都按它解析本地行，
        // 登录响应必须用同一把尺子，否则会出现「刚登录显示 A，一刷新变成 B」的分裂观感。
        UserEntity byAuthUserId = findLocalUserByAuthUserId(authUserId, account);
        UserEntity user = byAuthUserId != null ? byAuthUserId : localUser;
        if (byAuthUserId != null && localUser != null && !byAuthUserId.getId().equals(localUser.getId())) {
            log.error("本地 users 存在重复账号行：account={} 命中 userId={}，但网关身份 auth_user_id={} 属于 userId={}；"
                            + "已按网关身份放行（与鉴权过滤器一致），重复行需人工合并",
                    account, localUser.getId(), authUserId, byAuthUserId.getId());
        }
        // 按账号名没落到权威行 = 本地镜像漂移，这时才需要自愈用户名
        boolean mirrorDrifted = localUser == null
                || (user != null && !user.getId().equals(localUser.getId()));
        if (user != null) {
            syncLocalMirror(user, authUserId, account, tokenResponse.getAccessToken(), rememberMe, mirrorDrifted);
        }
        String username = user != null && StringUtils.hasText(user.getUsername()) ? user.getUsername() : account;
        String role = user != null && StringUtils.hasText(user.getRole())
                ? user.getRole()
                : resolvePlatformRole(tokenResponse.getAccessToken(), authUserId);
        boolean onboardingDone = user == null || user.getOnboardingCompletedAt() != null;
        return AuthTokenResponse.builder()
                .token(tokenResponse.getAccessToken())
                .tokenType(StringUtils.hasText(tokenResponse.getTokenType()) ? tokenResponse.getTokenType() : "Bearer")
                .expiresIn(tokenResponse.getExpiresIn())
                .isNewUser(isNewUser)
                .onboardingCompleted(onboardingDone)
                .user(AuthTokenResponse.UserInfo.builder()
                        .userId(user != null ? user.getId() : Long.valueOf(authUserId))
                        .username(username)
                        .role(role)
                        .build())
                .build();
    }

    /** 本地拿不到角色时回落到网关 token 的 platform_role；解析失败按最小权限 USER 放行，不阻断登录。 */
    private String resolvePlatformRole(String accessToken, long authUserId) {
        try {
            return jwtTokenProvider.getPlatformRole(accessToken);
        } catch (Exception ex) {
            log.error("解析网关 platform_role 失败，按最小权限 USER 放行 authUserId={}", authUserId, ex);
            return "USER";
        }
    }

    /**
     * 本地 users 镜像按 auth_user_id 兜底解析。仅在「按账号名查不到本地行」时才会走到——
     * 中文用户名登录 500 正是这条路径炸的（网关认 auth_users 里的用户名，本地镜像已漂移）。
     * 失败必须留全栈（这是拿到根因的唯一现场），但不得阻断一次已认证成功的登录。
     */
    private UserEntity findLocalUserByAuthUserId(long authUserId, String account) {
        try {
            return userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                    .eq(UserEntity::getAuthUserId, authUserId)
                    .last("LIMIT 1"));
        } catch (Exception ex) {
            log.error("按 auth_user_id 兜底解析本地用户失败，已按网关身份放行 authUserId={} account={}",
                    authUserId, account, ex);
            return null;
        }
    }

    /** 登录成功后的本地镜像同步（回填 auth_user_id、记录会话、自愈用户名），整体 best-effort。 */
    private void syncLocalMirror(UserEntity user, long authUserId, String account, String accessToken,
                                 boolean rememberMe, boolean mirrorMissed) {
        try {
            if (!Long.valueOf(authUserId).equals(user.getAuthUserId()) && !backfillAuthUserId(user, authUserId)) {
                // 本地存在重复账号行，这一行认领不了网关身份，后续同步一律跳过，避免把脏值带进别的写操作
                return;
            }
            loginSessionRecorder.record(user.getId(), accessToken, rememberMe, currentHttpRequest());
            if (mirrorMissed) {
                healLocalUsername(user, account);
            }
        } catch (Exception ex) {
            log.error("登录后同步本地用户镜像失败，已按已解析身份放行 authUserId={} account={}",
                    authUserId, account, ex);
        }
    }

    /**
     * 账号名的权威在 auth-gateway。网关刚用 account 认证通过，若它既非邮箱也非手机号，
     * 那它就是网关侧的用户名；本地按账号名查不到该行即为漂移，就地自愈，避免下次登录再走兜底路径。
     * 仅在镜像未命中时调用，正常登录不产生任何额外写入。
     *
     * <p>刻意不做两件事：(1) 不用本地用户名正则再校验一遍——本地各写一套校验正是漂移成因；
     * (2) 不更新 username_updated_at——这是系统同步，不是用户改名，不应占用「30 天改一次」的配额。
     * 用只带 id + username 的 patch 实体更新，避免整行回写误伤其他列。</p>
     */
    private void healLocalUsername(UserEntity user, String account) {
        try {
            if (!StringUtils.hasText(account) || account.contains("@") || account.matches(MAINLAND_PHONE_PATTERN)) {
                return;
            }
            if (account.equals(user.getUsername())) {
                return;
            }
            long taken = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                    .eq(UserEntity::getUsername, account)
                    .ne(UserEntity::getId, user.getId()));
            if (taken > 0) {
                log.warn("本地用户名镜像自愈跳过：账号名已被其他行占用 userId={}", user.getId());
                return;
            }
            UserEntity patch = new UserEntity();
            patch.setId(user.getId());
            patch.setUsername(account);
            userMapper.updateById(patch);
            user.setUsername(account);
            log.info("本地用户名镜像自愈完成 userId={}", user.getId());
        } catch (Exception ex) {
            log.warn("本地用户名镜像自愈失败（不影响登录） userId={}: {}", user.getId(), ex.toString());
        }
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
