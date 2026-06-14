package com.careermate.auth.sms;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.audit.AuditActionType;
import com.careermate.audit.service.AuditService;
import com.careermate.auth.dto.PasswordResetConfirmRequest;
import com.careermate.auth.dto.PasswordResetConfirmResponse;
import com.careermate.auth.dto.PasswordResetSmsSendRequest;
import com.careermate.auth.dto.SmsSendResponse;
import com.careermate.common.api.ErrorCode;
import com.careermate.common.exception.BizException;
import com.careermate.common.web.ClientIpResolver;
import com.careermate.mapper.UserMapper;
import com.careermate.model.entity.UserEntity;
import com.careermate.observability.MdcKeys;
import com.careermate.security.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Slf4j
@Service
public class PasswordResetService {

    private static final long DEFAULT_COOLDOWN_SECONDS = 60L;
    private static final SmsScene SCENE = SmsScene.PASSWORD_RESET;

    private final MobileSmsAuthProvider mobileSmsAuthProvider;
    private final SmsAuthRateLimiter smsAuthRateLimiter;
    private final TokenReplayGuard tokenReplayGuard;
    private final AliyunSmsProperties smsProperties;
    private final SecurityProperties securityProperties;
    private final ClientIpResolver clientIpResolver;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public PasswordResetService(
            MobileSmsAuthProvider mobileSmsAuthProvider,
            SmsAuthRateLimiter smsAuthRateLimiter,
            TokenReplayGuard tokenReplayGuard,
            AliyunSmsProperties smsProperties,
            SecurityProperties securityProperties,
            ClientIpResolver clientIpResolver,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            AuditService auditService
    ) {
        this.mobileSmsAuthProvider = mobileSmsAuthProvider;
        this.smsAuthRateLimiter = smsAuthRateLimiter;
        this.tokenReplayGuard = tokenReplayGuard;
        this.smsProperties = smsProperties;
        this.securityProperties = securityProperties;
        this.clientIpResolver = clientIpResolver;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public SmsSendResponse sendSms(PasswordResetSmsSendRequest request) {
        String phone = PhoneSupport.normalizePhone(request.getPhone());
        PhoneSupport.validateMainlandPhone(phone);
        String traceId = currentTraceId();
        String maskedPhone = PhoneSupport.maskPhone(phone);
        String phoneHash = phoneHash(phone);
        String ipHash = ipHash(resolveClientIp());

        try {
            smsAuthRateLimiter.checkSendAllowed(SCENE, phoneHash, ipHash, maskedPhone, traceId);
        } catch (BizException ex) {
            mapRateLimitException(ex);
        }

        UserEntity user = findUserByPhone(phone);
        boolean activeUser = user != null && isActive(user);

        String providerOutId = null;
        String providerRequestId = null;
        if (activeUser) {
            MobileSmsAuthProvider.SendResult sendResult = mobileSmsAuthProvider.sendVerifyCode(
                    MobileSmsAuthProvider.SendRequest.builder()
                            .phone(phone)
                            .scene(SCENE)
                            .build()
            );
            if (!sendResult.isSuccess()) {
                auditService.recordFailure(user.getId(), AuditActionType.PASSWORD_RESET_SMS_SEND, "SMS",
                        SCENE.value(), "provider send failed phone=" + maskedPhone);
                throw new BizException(ErrorCode.PASSWORD_RESET_PROVIDER_ERROR);
            }
            providerOutId = sendResult.getOutId();
            providerRequestId = sendResult.getProviderRequestId();
        } else {
            log.info("Password reset send skipped SMS for inactive or missing user, phone={}, traceId={}",
                    maskedPhone, traceId);
        }

        String challengeId = StringUtils.hasText(providerOutId)
                ? providerOutId
                : UUID.randomUUID().toString();
        String challengeHash = TokenReplayGuard.hashOutId(challengeId, pepper());
        smsAuthRateLimiter.storePendingChallenge(SCENE, phoneHash, challengeHash, providerOutId);

        smsAuthRateLimiter.recordSend(SCENE, phoneHash, ipHash);
        auditService.recordSuccess(
                user != null ? user.getId() : null,
                AuditActionType.PASSWORD_RESET_SMS_SEND,
                "SMS",
                SCENE.value(),
                "send phone=" + maskedPhone + ", smsSent=" + activeUser
                        + ", providerRequestId=" + providerRequestId
        );
        log.info("Password reset send success, phone={}, traceId={}, smsSent={}",
                maskedPhone, traceId, activeUser);

        long cooldown = smsAuthRateLimiter.sendCooldownRemainingSeconds(SCENE, phoneHash);
        return SmsSendResponse.builder()
                .cooldownSeconds(cooldown > 0 ? cooldown : DEFAULT_COOLDOWN_SECONDS)
                .challengeId(challengeId)
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public PasswordResetConfirmResponse confirm(PasswordResetConfirmRequest request) {
        String phone = PhoneSupport.normalizePhone(request.getPhone());
        PhoneSupport.validateMainlandPhone(phone);
        String verifyCode = request.getVerifyCode().trim();
        String challengeId = request.getChallengeId().trim();
        String newPassword = request.getNewPassword();

        String traceId = currentTraceId();
        String maskedPhone = PhoneSupport.maskPhone(phone);
        String phoneHash = phoneHash(phone);
        String ipHash = ipHash(resolveClientIp());
        String challengeHash = TokenReplayGuard.hashOutId(challengeId, pepper());

        try {
            smsAuthRateLimiter.checkLoginAllowed(SCENE, phoneHash, ipHash, maskedPhone, traceId);
        } catch (BizException ex) {
            mapRateLimitException(ex);
        }
        smsAuthRateLimiter.recordLoginAttempt(SCENE, phoneHash, ipHash);
        assertChallengeNotUsed(challengeHash);

        if (!smsAuthRateLimiter.matchesPendingChallenge(SCENE, phoneHash, challengeHash)) {
            smsAuthRateLimiter.recordLoginFailure(SCENE, phoneHash);
            auditFailure(null, "challenge mismatch phone=" + maskedPhone);
            throw new BizException(ErrorCode.PASSWORD_RESET_INVALID);
        }

        UserEntity user = findUserByPhone(phone);
        if (user == null || !isActive(user)) {
            smsAuthRateLimiter.recordLoginFailure(SCENE, phoneHash);
            auditFailure(user != null ? user.getId() : null, "user not eligible phone=" + maskedPhone);
            throw new BizException(ErrorCode.PASSWORD_RESET_INVALID);
        }

        String providerOutId = smsAuthRateLimiter.getPendingProviderOutId(SCENE, phoneHash).orElse(null);
        MobileSmsAuthProvider.VerifyResult verifyResult = mobileSmsAuthProvider.checkVerifyCode(
                MobileSmsAuthProvider.VerifyRequest.builder()
                        .phone(phone)
                        .verifyCode(verifyCode)
                        .outId(providerOutId)
                        .scene(SCENE)
                        .build()
        );

        if (!verifyResult.isSuccess()) {
            smsAuthRateLimiter.recordLoginFailure(SCENE, phoneHash);
            auditFailure(user.getId(), "verify failed phone=" + maskedPhone);
            throw new BizException(ErrorCode.PASSWORD_RESET_INVALID);
        }

        String verifiedPhone = PhoneSupport.normalizePhone(verifyResult.getPhone());
        if (!phone.equals(verifiedPhone)) {
            smsAuthRateLimiter.recordLoginFailure(SCENE, phoneHash);
            auditFailure(user.getId(), "phone mismatch phone=" + maskedPhone);
            throw new BizException(ErrorCode.PASSWORD_RESET_INVALID);
        }

        markChallengeUsed(challengeHash);
        smsAuthRateLimiter.clearLoginFailure(SCENE, phoneHash);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);

        auditService.recordSuccess(user.getId(), AuditActionType.PASSWORD_RESET, "USER",
                String.valueOf(user.getId()), "password reset phone=" + maskedPhone);
        log.info("Password reset success, phone={}, traceId={}", maskedPhone, traceId);

        return PasswordResetConfirmResponse.builder().success(true).build();
    }

    private void mapRateLimitException(BizException ex) {
        if (ex.getCode() == ErrorCode.SMS_SEND_TOO_FREQUENT.getCode()
                || ex.getCode() == ErrorCode.SMS_SEND_LIMITED.getCode()
                || ex.getCode() == ErrorCode.MOBILE_AUTH_LIMITED.getCode()
                || ex.getCode() == ErrorCode.MOBILE_AUTH_TOO_MANY_ATTEMPTS.getCode()) {
            throw new BizException(ErrorCode.PASSWORD_RESET_LIMITED);
        }
        throw ex;
    }

    private UserEntity findUserByPhone(String phone) {
        return userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getPhone, phone)
                .last("LIMIT 1"));
    }

    private boolean isActive(UserEntity user) {
        return "ACTIVE".equalsIgnoreCase(user.getStatus());
    }

    private void assertChallengeNotUsed(String challengeHash) {
        if (!StringUtils.hasText(challengeHash)) {
            throw new BizException(ErrorCode.PASSWORD_RESET_INVALID);
        }
        try {
            tokenReplayGuard.assertChallengeNotUsed(SCENE, challengeHash);
        } catch (BizException ex) {
            if (ex.getCode() == ErrorCode.MOBILE_AUTH_EXPIRED.getCode()
                    || ex.getCode() == ErrorCode.MOBILE_AUTH_INVALID.getCode()) {
                throw new BizException(ErrorCode.PASSWORD_RESET_INVALID);
            }
            throw ex;
        }
    }

    private void markChallengeUsed(String challengeHash) {
        tokenReplayGuard.markChallengeUsed(SCENE, challengeHash);
    }

    private void auditFailure(Long userId, String detail) {
        auditService.recordFailure(userId, AuditActionType.PASSWORD_RESET, "USER",
                userId != null ? String.valueOf(userId) : null, detail);
    }

    private String pepper() {
        if (StringUtils.hasText(smsProperties.getPhoneHashPepper())) {
            return smsProperties.getPhoneHashPepper();
        }
        return securityProperties.getJwt().getSecret();
    }

    private String phoneHash(String phone) {
        return PhoneSupport.hashPhone(phone, pepper());
    }

    private String ipHash(String ip) {
        return PhoneSupport.hashIp(ip, pepper());
    }

    private String resolveClientIp() {
        return clientIpResolver.resolve(currentRequest());
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    private String currentTraceId() {
        String traceId = MDC.get(MdcKeys.TRACE_ID);
        if (StringUtils.hasText(traceId)) {
            return traceId;
        }
        String requestId = MDC.get(MdcKeys.REQUEST_ID);
        return StringUtils.hasText(requestId) ? requestId : "unknown";
    }
}
