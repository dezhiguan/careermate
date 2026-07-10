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

    public UserSettingsService(
            UserMapper userMapper,
            UserLoginSessionMapper sessionMapper,
            UserPasswordHistoryMapper passwordHistoryMapper,
            PasswordEncoder passwordEncoder,
            SmsAuthRateLimiter smsAuthRateLimiter,
            TokenReplayGuard tokenReplayGuard,
            AliyunSmsProperties smsProperties,
            SecurityProperties securityProperties,
            AuthGatewayClient authGatewayClient
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
        verifySmsChallenge(user.getPhone(), request.getChallengeId(), request.getVerifyCode(), SmsScene.MOBILE_LOGIN);
        user.setUsername(newUsername);
        user.setUsernameUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        userMapper.updateById(user);
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
        verifySmsChallenge(user.getPhone(), request.getChallengeId(), request.getVerifyCode(), SmsScene.MOBILE_LOGIN);
        checkPasswordHistory(user.getId(), newPassword);
        String hash = passwordEncoder.encode(newPassword);
        user.setPasswordHash(hash);
        user.setSessionVersion(user.getSessionVersion() == null ? 1L : user.getSessionVersion() + 1);
        userMapper.updateById(user);
        savePasswordHistory(user.getId(), hash);
        log.info("User {} set password, session_version={}", user.getId(), user.getSessionVersion());
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
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        user.setStatus("CANCELLING");
        user.setPendingDeletionAt(now);
        user.setDeletionScheduledAt(now.plusDays(7));
        userMapper.updateById(user);
        log.info("User {} requested account cancellation, scheduled deletion at {}", user.getId(), user.getDeletionScheduledAt());
    }

    @Transactional(rollbackFor = Exception.class)
    public void revokeCancellation() {
        UserEntity user = requireCurrentUser();
        if (!"CANCELLING".equalsIgnoreCase(user.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_CANCELLING);
        }
        user.setStatus("ACTIVE");
        user.setPendingDeletionAt(null);
        user.setDeletionScheduledAt(null);
        userMapper.updateById(user);
        log.info("User {} revoked account cancellation", user.getId());
    }

    // ─── 会话管理────────────────────────────────────────────────────────────────

    public List<SessionInfoResponse> listSessions(String currentSessionId) {
        UserEntity user = requireCurrentUser();
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
        authGatewayClient.revokeSession(sessionId);
        log.info("User {} revoked session {}", user.getId(), sessionId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void revokeAllOtherSessions(String currentSessionId) {
        UserEntity user = requireCurrentUser();
        sessionMapper.delete(new LambdaQueryWrapper<UserLoginSessionEntity>()
                .eq(UserLoginSessionEntity::getUserId, user.getId())
                .ne(UserLoginSessionEntity::getId, currentSessionId));
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
