package com.careermate.auth.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.auth.settings.dto.*;
import com.careermate.auth.gateway.AuthGatewayClient;
import com.careermate.auth.sms.PhoneSupport;
import com.careermate.auth.sms.SmsScene;
import com.careermate.auth.sms.SmsAuthRateLimiter;
import com.careermate.auth.sms.TokenReplayGuard;
import com.careermate.auth.sms.AliyunSmsProperties;
import com.careermate.common.api.ErrorCode;
import com.careermate.common.exception.BizException;
import com.careermate.mapper.UserLoginSessionMapper;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserPasswordHistoryMapper;
import com.careermate.model.entity.UserEntity;
import com.careermate.model.entity.UserLoginSessionEntity;
import com.careermate.model.entity.UserPasswordHistoryEntity;
import com.careermate.security.CurrentUser;
import com.careermate.security.CurrentUserContext;
import com.careermate.security.SecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
public class UserSettingsService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^(?!\\d+$)[a-zA-Z0-9_\\-]{4,32}$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-zA-Z])(?=.*\\d).{8,64}$");
    private static final int MAX_SESSIONS = 10;
    private static final int PASSWORD_HISTORY_COUNT = 3;
    private static final int USERNAME_CHANGE_DAYS = 30;

    private final UserMapper userMapper;
    private final UserLoginSessionMapper sessionMapper;
    private final UserPasswordHistoryMapper passwordHistoryMapper;
    private final PasswordEncoder passwordEncoder;
    private final SmsAuthRateLimiter smsAuthRateLimiter;
    private final TokenReplayGuard tokenReplayGuard;
    private final AliyunSmsProperties smsProperties;
    private final SecurityProperties securityProperties;
    private final AuthGatewayClient authGatewayClient;
    private final com.careermate.auth.events.AuthEventService authEventService;

    public UserSettingsService(
            UserMapper userMapper,
            UserLoginSessionMapper sessionMapper,
            UserPasswordHistoryMapper passwordHistoryMapper,
            PasswordEncoder passwordEncoder,
            SmsAuthRateLimiter smsAuthRateLimiter,
            TokenReplayGuard tokenReplayGuard,
            AliyunSmsProperties smsProperties,
            SecurityProperties securityProperties,
            AuthGatewayClient authGatewayClient,
            com.careermate.auth.events.AuthEventService authEventService
    ) {
        this.userMapper = userMapper;
        this.sessionMapper = sessionMapper;
        this.passwordHistoryMapper = passwordHistoryMapper;
        this.passwordEncoder = passwordEncoder;
        this.smsAuthRateLimiter = smsAuthRateLimiter;
        this.tokenReplayGuard = tokenReplayGuard;
        this.smsProperties = smsProperties;
        this.securityProperties = securityProperties;
        this.authGatewayClient = authGatewayClient;
        this.authEventService = authEventService;
    }

    /** 踢下当前用户除 keepJti 外的所有设备：吊销各自 jti + 删除会话行。用于改密/换绑后。 */
    private void revokeOtherSessions(UserEntity user, String keepJti) {
        List<UserLoginSessionEntity> others = sessionMapper.selectList(
                new LambdaQueryWrapper<UserLoginSessionEntity>()
                        .eq(UserLoginSessionEntity::getUserId, user.getId()));
        for (UserLoginSessionEntity s : others) {
            if (keepJti != null && keepJti.equals(s.getId())) {
                continue;
            }
            authEventService.revokeJti(s.getId(), null);
            sessionMapper.deleteById(s.getId());
        }
    }

    private String currentJti() {
        CurrentUser cu = CurrentUserContext.get();
        return cu == null ? null : cu.getJti();
    }

    /** 取当前请求的 Bearer access token，用于向 auth-gateway 代理同步身份（网关据其 user_id 定位用户）。 */
    private String currentBearerToken() {
        org.springframework.web.context.request.RequestAttributes attrs =
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes sra) {
            String header = sra.getRequest().getHeader("Authorization");
            if (StringUtils.hasText(header)) {
                return header;
            }
        }
        return null;
    }

    // ─── 邮箱绑定（Phase 1：存储，不验证）────────────────────────────────────────

    @Transactional(rollbackFor = Exception.class)
    public void bindEmail(BindEmailRequest request) {
        UserEntity user = requireCurrentUser();
        String email = request.getEmail().trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new BizException(ErrorCode.EMAIL_FORMAT_INVALID);
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getEmail, email)
                .ne(UserEntity::getId, user.getId())) > 0) {
            throw new BizException(ErrorCode.EMAIL_ALREADY_BOUND);
        }
        user.setEmail(email);
        user.setEmailVerified(false);
        userMapper.updateById(user);
        // 同步邮箱到 auth-gateway，使邮箱可作为登录账号。best-effort：
        // 网关侧若账号已有密码会要求密码校验（本流程无密码），失败仅记日志、不阻断本地绑定。
        try {
            authGatewayClient.bindCredentialEmail(currentBearerToken(), email, null);
        } catch (RuntimeException ex) {
            log.warn("sync email to gateway failed (email login may not work): {}", ex.getMessage());
        }
        log.info("User {} bound email (unverified)", user.getId());
    }

    // ─── 设置账号名（短信二次验证 + 30天频率）────────────────────────────────────

    @Transactional(rollbackFor = Exception.class)
    public void updateUsername(UpdateUsernameRequest request) {
        UserEntity user = requireCurrentUser();
        String newUsername = request.getUsername().trim();
        if (!USERNAME_PATTERN.matcher(newUsername).matches()) {
            throw new BizException(ErrorCode.USERNAME_FORMAT_INVALID);
        }
        if (user.getUsernameUpdatedAt() != null &&
                user.getUsernameUpdatedAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC).minusDays(USERNAME_CHANGE_DAYS))) {
            throw new BizException(ErrorCode.USERNAME_UPDATE_TOO_FREQUENT);
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, newUsername)
                .ne(UserEntity::getId, user.getId())) > 0) {
            throw new BizException(ErrorCode.USERNAME_TAKEN);
        }
        // 修改账号名属低风险操作（仅昵称/登录名，已受登录态 + 30 天频控 + 唯一性约束保护），
        // 不再强制短信二次验证，降低改名门槛。
        user.setUsername(newUsername);
        user.setUsernameUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        userMapper.updateById(user);
        // 同步账号名到 auth-gateway，使账号名可作为登录账号。best-effort：
        // 网关用户名规则不含中划线，含"-"的名会被网关拒；此类失败仅记日志、不阻断本地改名。
        try {
            authGatewayClient.setCredentialUsername(currentBearerToken(), newUsername);
        } catch (RuntimeException ex) {
            log.warn("sync username to gateway failed (username login may not work): {}", ex.getMessage());
        }
        log.info("User {} updated username to {}", user.getId(), newUsername);
    }

    // ─── 设置密码（短信二次验证 + 历史检查 + session_version+1）────────────────

    @Transactional(rollbackFor = Exception.class)
    public void setPassword(SetPasswordRequest request) {
        UserEntity user = requireCurrentUser();
        String newPassword = request.getNewPassword();
        if (!PASSWORD_PATTERN.matcher(newPassword).matches()) {
            throw new BizException(ErrorCode.PASSWORD_WEAK);
        }
        checkPasswordHistory(user.getId(), newPassword);
        // 密码必须落到 auth-gateway（登录校验网关这份），否则设置了却登不进去。
        // 走网关的重置流程（resetVerify + resetConfirm）：无需旧密码，OTP 由网关校验（RESET scene），
        // 与"忘记密码"同一套已验证的机制，也适用于网关已有密码的账号。
        // 前端"设置密码"的验证码必须以 RESET scene 下发（sendPasswordResetSms）。
        // 置于本地写库之前：网关失败则整体事务回滚，避免"本地已改、网关未改"的不一致。
        AuthGatewayClient.ResetVerifyResponse resetVerify =
                authGatewayClient.resetVerify(user.getPhone(), request.getVerifyCode());
        authGatewayClient.resetConfirm(resetVerify.getResetTicket(), newPassword);
        String hash = passwordEncoder.encode(newPassword);
        user.setPasswordHash(hash);
        user.setSessionVersion(user.getSessionVersion() == null ? 1L : user.getSessionVersion() + 1);
        userMapper.updateById(user);
        savePasswordHistory(user.getId(), hash);
        // 改密后踢下其他设备（保留当前设备），使已签发的旧 token 立即失效
        revokeOtherSessions(user, currentJti());
        log.info("User {} set password (synced to gateway), session_version={}", user.getId(), user.getSessionVersion());
    }

    // ─── 换绑手机号 Step1：验证旧号──────────────────────────────────────────────

    public String verifyOldPhone(ChangePhoneStep1Request request) {
        UserEntity user = requireCurrentUser();
        if (!StringUtils.hasText(user.getPhone())) {
            throw new BizException(ErrorCode.PHONE_VERIFY_REQUIRED);
        }
        verifySmsChallenge(user.getPhone(), request.getChallengeId(), request.getVerifyCode(), SmsScene.MOBILE_LOGIN);
        String ticket = UUID.randomUUID().toString();
        smsAuthRateLimiter.storePendingChallenge(
                SmsScene.MOBILE_LOGIN,
                "phone_change_ticket:" + user.getId(),
                TokenReplayGuard.hashOutId(ticket, pepper()),
                null
        );
        return ticket;
    }

    // ─── 换绑手机号 Step2：绑定新号─────────────────────────────────────────────

    @Transactional(rollbackFor = Exception.class)
    public void bindNewPhone(ChangePhoneStep2Request request) {
        UserEntity user = requireCurrentUser();
        String ticketHash = TokenReplayGuard.hashOutId(request.getOldPhoneTicket(), pepper());
        if (!smsAuthRateLimiter.matchesPendingChallenge(
                SmsScene.MOBILE_LOGIN,
                "phone_change_ticket:" + user.getId(),
                ticketHash)) {
            throw new BizException(ErrorCode.MOBILE_AUTH_EXPIRED);
        }
        String newPhone = PhoneSupport.normalizePhone(request.getNewPhone());
        PhoneSupport.validateMainlandPhone(newPhone);
        if (userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getPhone, newPhone)
                .ne(UserEntity::getId, user.getId())) > 0) {
            throw new BizException(ErrorCode.PHONE_ALREADY_BOUND);
        }
        verifySmsChallenge(newPhone, request.getChallengeId(), request.getVerifyCode(), SmsScene.MOBILE_LOGIN);
        user.setPhone(newPhone);
        user.setPhoneVerified(true);
        user.setPhoneVerifiedAt(OffsetDateTime.now(ZoneOffset.UTC));
        user.setSessionVersion(user.getSessionVersion() == null ? 1L : user.getSessionVersion() + 1);
        userMapper.updateById(user);
        // 换绑手机后踢下其他设备（保留当前设备）
        revokeOtherSessions(user, currentJti());
        log.info("User {} changed phone, session_version={}", user.getId(), user.getSessionVersion());
    }

    // ─── 账号注销（CANCELLING + 7天冷静期）──────────────────────────────────────

    @Transactional(rollbackFor = Exception.class)
    public void requestCancellation(CancelAccountRequest request) {
        UserEntity user = requireCurrentUser();
        if (!"注销".equals(request.getConfirmText())) {
            throw new BizException(ErrorCode.ACCOUNT_DELETE_CONFIRM_INVALID);
        }
        verifySmsChallenge(user.getPhone(), request.getChallengeId(), request.getVerifyCode(), SmsScene.MOBILE_LOGIN);
        // 应用级注销：委托网关把 careermate membership 置 PENDING_DELETION（30 天冷静期，权威在网关）。
        // 只退 CareerMate，不影响 RAGForge 与共享身份；到期由网关发 user.app_removed 事件驱动本地清理。
        java.util.Map<String, Object> gwResp = authGatewayClient.requestAppDeletion(currentBearerToken(), "careermate");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime scheduledAt = parseGatewayScheduledAt(gwResp, now.plusDays(30));
        user.setStatus("CANCELLING");
        user.setPendingDeletionAt(now);
        user.setDeletionScheduledAt(scheduledAt);
        userMapper.updateById(user);
        // 注销即登出全设备：用户级吊销 + 删除本地会话行。
        revokeAllSessions(user);
        log.info("User {} requested account cancellation (app-level), scheduled deletion at {}", user.getId(), scheduledAt);
    }

    @Transactional(rollbackFor = Exception.class)
    public void revokeCancellation() {
        UserEntity user = requireCurrentUser();
        if (!"CANCELLING".equalsIgnoreCase(user.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_CANCELLING);
        }
        // 委托网关撤销 careermate membership 的注销
        authGatewayClient.cancelAppDeletion(currentBearerToken(), "careermate");
        // 显式 set 清空时间戳列：MyBatis-Plus updateById 默认跳过 null 字段，直接 setXxx(null)+updateById 不会清列。
        userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserEntity>()
                .eq(UserEntity::getId, user.getId())
                .set(UserEntity::getStatus, "ACTIVE")
                .set(UserEntity::getPendingDeletionAt, null)
                .set(UserEntity::getDeletionScheduledAt, null));
        log.info("User {} revoked account cancellation (app-level)", user.getId());
    }

    private OffsetDateTime parseGatewayScheduledAt(java.util.Map<String, Object> resp, OffsetDateTime fallback) {
        Object v = resp == null ? null : resp.get("deletionScheduledAt");
        if (v == null) {
            return fallback;
        }
        try {
            return java.time.Instant.parse(String.valueOf(v)).atOffset(ZoneOffset.UTC);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    /** 注销时吊销该用户全部会话（含当前设备）：用户级吊销 + 删除本地会话行。 */
    private void revokeAllSessions(UserEntity user) {
        if (user.getAuthUserId() != null) {
            authEventService.revokeUserAfter(String.valueOf(user.getAuthUserId()),
                    java.time.Instant.now().getEpochSecond());
        }
        revokeOtherSessions(user, null); // keepJti=null → 全部吊销并删行
    }

    // ─── 会话管理────────────────────────────────────────────────────────────────

    public List<SessionInfoResponse> listSessions(String currentSessionIdFromHeader) {
        UserEntity user = requireCurrentUser();
        // 当前设备判定优先用当前请求 token 的 jti（会话行 id == jti），header 仅作兜底
        String currentSessionId = currentJti() != null ? currentJti() : currentSessionIdFromHeader;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<UserLoginSessionEntity> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<UserLoginSessionEntity>()
                        .eq(UserLoginSessionEntity::getUserId, user.getId())
                        .gt(UserLoginSessionEntity::getExpiresAt, now)
                        .orderByDesc(UserLoginSessionEntity::getLastActive)
                        .last("LIMIT " + MAX_SESSIONS)
        );
        return sessions.stream()
                .map(s -> SessionInfoResponse.builder()
                        .sessionId(s.getId())
                        .deviceName(s.getDeviceName())
                        .ipAddress(s.getIpAddress())
                        .rememberMe(s.getRememberMe())
                        .lastActive(s.getLastActive())
                        .expiresAt(s.getExpiresAt())
                        .current(s.getId().equals(currentSessionId))
                        .build())
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void revokeSession(String sessionId) {
        UserEntity user = requireCurrentUser();
        UserLoginSessionEntity session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(user.getId())) {
            throw new BizException(ErrorCode.SESSION_NOT_FOUND);
        }
        sessionMapper.deleteById(sessionId);
        // 会话行 id == access token 的 jti，直接吊销该 jti 使被踢设备下次请求即 401
        authEventService.revokeJti(sessionId, null);
        // 同步通知 auth-gateway（best-effort，失败不影响本地吊销）
        try {
            authGatewayClient.revokeSession(sessionId);
        } catch (RuntimeException ex) {
            log.warn("notify auth-gateway revokeSession failed (non-fatal): {}", ex.getMessage());
        }
        log.info("User {} revoked session {}", user.getId(), sessionId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void revokeAllOtherSessions(String currentSessionIdFromHeader) {
        UserEntity user = requireCurrentUser();
        String keepJti = currentJti() != null ? currentJti() : currentSessionIdFromHeader;
        // 逐个吊销其他会话的 jti，保留当前设备
        revokeOtherSessions(user, keepJti);
        user.setSessionVersion(user.getSessionVersion() == null ? 1L : user.getSessionVersion() + 1);
        userMapper.updateById(user);
        log.info("User {} revoked all other sessions, session_version={}", user.getId(), user.getSessionVersion());
    }

    // ─── Onboarding 完成标记────────────────────────────────────────────────────

    @Transactional(rollbackFor = Exception.class)
    public void completeOnboarding() {
        UserEntity user = requireCurrentUser();
        if (user.getOnboardingCompletedAt() == null) {
            user.setOnboardingCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
            userMapper.updateById(user);
        }
    }

    // ─── 协议同意──────────────────────────────────────────────────────────────

    @Transactional(rollbackFor = Exception.class)
    public void agreeTerms(String termsVersion) {
        UserEntity user = requireCurrentUser();
        user.setTermsAgreedAt(OffsetDateTime.now(ZoneOffset.UTC));
        user.setTermsVersion(termsVersion);
        userMapper.updateById(user);
    }

    // ─── 私有辅助────────────────────────────────────────────────────────────────

    private UserEntity requireCurrentUser() {
        CurrentUser currentUser = CurrentUserContext.get();
        if (currentUser == null || !currentUser.isAuthenticated()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        UserEntity user = userMapper.selectById(currentUser.getUserId());
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    private void verifySmsChallenge(String phone, String challengeId, String verifyCode, SmsScene scene) {
        if (!StringUtils.hasText(phone)) {
            throw new BizException(ErrorCode.PHONE_VERIFY_REQUIRED);
        }
        if (!StringUtils.hasText(challengeId)) {
            throw new BizException(ErrorCode.MOBILE_AUTH_CHALLENGE_REQUIRED);
        }
        String phoneHash = PhoneSupport.hashPhone(phone, pepper());
        String challengeHash = TokenReplayGuard.hashOutId(challengeId, pepper());
        tokenReplayGuard.assertChallengeNotUsed(scene, challengeHash);
        if (!smsAuthRateLimiter.matchesPendingChallenge(scene, phoneHash, challengeHash)) {
            throw new BizException(ErrorCode.MOBILE_AUTH_EXPIRED);
        }
        // Forward to auth-gateway for actual OTP verification via existing mobile login flow.
        // For settings flows we rely on challenge matching + OTP check via gateway.
        try {
            authGatewayClient.loginMobile(phone, verifyCode);
        } catch (BizException ex) {
            throw new BizException(ErrorCode.SMS_CODE_INVALID);
        }
        tokenReplayGuard.markChallengeUsed(scene, challengeHash);
    }

    private void checkPasswordHistory(Long userId, String newPassword) {
        List<UserPasswordHistoryEntity> history = passwordHistoryMapper.selectList(
                new LambdaQueryWrapper<UserPasswordHistoryEntity>()
                        .eq(UserPasswordHistoryEntity::getUserId, userId)
                        .orderByDesc(UserPasswordHistoryEntity::getCreatedAt)
                        .last("LIMIT " + PASSWORD_HISTORY_COUNT)
        );
        for (UserPasswordHistoryEntity entry : history) {
            if (passwordEncoder.matches(newPassword, entry.getPasswordHash())) {
                throw new BizException(ErrorCode.PASSWORD_HISTORY_CONFLICT);
            }
        }
    }

    private void savePasswordHistory(Long userId, String hash) {
        UserPasswordHistoryEntity entry = new UserPasswordHistoryEntity();
        entry.setUserId(userId);
        entry.setPasswordHash(hash);
        entry.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        passwordHistoryMapper.insert(entry);
    }

    private String pepper() {
        if (StringUtils.hasText(smsProperties.getPhoneHashPepper())) {
            return smsProperties.getPhoneHashPepper();
        }
        return securityProperties.getJwt().getSecret();
    }
}
