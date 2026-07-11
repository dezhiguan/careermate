package com.careermate.auth.sms;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.audit.AuditActionType;
import com.careermate.audit.service.AuditService;
import com.careermate.auth.dto.AuthTokenResponse;
import com.careermate.auth.dto.MobileLoginRequest;
import com.careermate.auth.dto.SmsSendRequest;
import com.careermate.auth.dto.SmsSendResponse;
import com.careermate.auth.gateway.AuthGatewayClient;
import com.careermate.auth.gateway.AuthGatewayCookieSupport;
import com.careermate.common.api.ErrorCode;
import com.careermate.common.exception.BizException;
import com.careermate.common.web.ClientIpResolver;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.model.entity.UserEntity;
import com.careermate.model.entity.UserProfileEntity;
import com.careermate.observability.MdcKeys;
import com.careermate.security.JwtTokenProvider;
import com.careermate.security.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
public class MobileAuthService {

    private static final long DEFAULT_COOLDOWN_SECONDS = 60L;
    private static final int MAX_REGISTER_ATTEMPTS = 5;
    private static final ConcurrentMap<String, Object> MOBILE_REGISTER_LOCKS = new ConcurrentHashMap<>();

    private final SmsAuthRateLimiter smsAuthRateLimiter;
    private final AliyunSmsProperties smsProperties;
    private final SecurityProperties securityProperties;
    private final ClientIpResolver clientIpResolver;
    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditService auditService;
    private final AuthGatewayClient authGatewayClient;
    private final AuthGatewayCookieSupport cookieSupport;
    private final TokenReplayGuard tokenReplayGuard;
    private final com.careermate.auth.session.LoginSessionRecorder loginSessionRecorder;

    public MobileAuthService(
            SmsAuthRateLimiter smsAuthRateLimiter,
            AliyunSmsProperties smsProperties,
            SecurityProperties securityProperties,
            ClientIpResolver clientIpResolver,
            UserMapper userMapper,
            UserProfileMapper userProfileMapper,
            JwtTokenProvider jwtTokenProvider,
            AuditService auditService,
            AuthGatewayClient authGatewayClient,
            AuthGatewayCookieSupport cookieSupport,
            TokenReplayGuard tokenReplayGuard,
            com.careermate.auth.session.LoginSessionRecorder loginSessionRecorder
    ) {
        this.smsAuthRateLimiter = smsAuthRateLimiter;
        this.smsProperties = smsProperties;
        this.securityProperties = securityProperties;
        this.clientIpResolver = clientIpResolver;
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.auditService = auditService;
        this.authGatewayClient = authGatewayClient;
        this.cookieSupport = cookieSupport;
        this.tokenReplayGuard = tokenReplayGuard;
        this.loginSessionRecorder = loginSessionRecorder;
    }

    public SmsSendResponse sendCode(SmsSendRequest request) {
        SmsScene scene = resolveScene(request.getScene());
        String phone = PhoneSupport.normalizePhone(request.getPhone());
        PhoneSupport.validateMainlandPhone(phone);
        String traceId = currentTraceId();
        String maskedPhone = PhoneSupport.maskPhone(phone);
        String phoneHash = phoneHash(phone);

        if (requiresCaptcha(request)) {
            throw new BizException(ErrorCode.SMS_CAPTCHA_REQUIRED);
        }

        // 发码限流收口到 Auth Gateway：网关(/auth/sms/send)统一强制冷却/每日/每IP限额，
        // 并对 careermate 与 RAGForge 同号共享同一份计数；超限时网关返回 429，由 AuthGatewayClient 翻译上抛。
        // careermate 不再维护本地发码限流（避免与网关双重、且两产品行为不一致）。
        authGatewayClient.sendSms(phone, "login");
        String providerOutId = null;
        String challengeId = UUID.randomUUID().toString();
        String challengeHash = TokenReplayGuard.hashOutId(challengeId, pepper());
        smsAuthRateLimiter.storePendingChallenge(scene, phoneHash, challengeHash, providerOutId);

        auditService.recordSuccess(null, AuditActionType.SMS_SEND, "SMS", scene.value(),
                "send phone=" + maskedPhone + ", provider=auth-gateway");
        log.info("Mobile auth send success, phone={}, scene={}, traceId={}, provider=auth-gateway",
                maskedPhone, scene.value(), traceId);

        return SmsSendResponse.builder()
                .cooldownSeconds(DEFAULT_COOLDOWN_SECONDS)
                .challengeId(challengeId)
                .outId(providerOutId)
                .build();
    }

    public AuthTokenResponse login(MobileLoginRequest request) {
        SmsScene scene = resolveScene(request.getScene());
        String phone = PhoneSupport.normalizePhone(request.getPhone());
        PhoneSupport.validateMainlandPhone(phone);
        String verifyCode = request.getVerifyCode().trim();
        String traceId = currentTraceId();
        String maskedPhone = PhoneSupport.maskPhone(phone);
        String phoneHash = phoneHash(phone);
        String ipHash = ipHash(resolveClientIp());

        smsAuthRateLimiter.checkLoginAllowed(scene, phoneHash, ipHash, maskedPhone, traceId);
        smsAuthRateLimiter.recordLoginAttempt(scene, phoneHash, ipHash);

        String challengeId = request.resolveChallengeId();
        if (challengeId == null) {
            smsAuthRateLimiter.recordLoginFailure(scene, phoneHash);
            throw new BizException(ErrorCode.MOBILE_AUTH_EXPIRED);
        }
        String challengeHash = TokenReplayGuard.hashOutId(challengeId, pepper());
        tokenReplayGuard.assertChallengeNotUsed(scene, challengeHash);
        if (!smsAuthRateLimiter.matchesPendingChallenge(scene, phoneHash, challengeHash)) {
            smsAuthRateLimiter.recordLoginFailure(scene, phoneHash);
            throw new BizException(ErrorCode.MOBILE_AUTH_EXPIRED);
        }

        AuthGatewayClient.TokenResponse tokenResponse = request.isRememberMe()
                ? authGatewayClient.loginMobile(phone, verifyCode, true)
                : authGatewayClient.loginMobile(phone, verifyCode);
        smsAuthRateLimiter.clearLoginFailure(scene, phoneHash);
        tokenReplayGuard.markChallengeUsed(scene, challengeHash);

        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getPhone, phone)
                .last("LIMIT 1"));

        boolean isNewUser;
        if (user == null) {
            MobileUserLookup lookup = findOrCreateMobileUser(phone);
            user = lookup.user();
            isNewUser = lookup.newlyCreated();
        } else {
            isNewUser = false;
            if (isMobileLoginBlocked(user)) {
                auditService.recordFailure(user.getId(), AuditActionType.MOBILE_LOGIN, "USER",
                        String.valueOf(user.getId()), "user inactive");
                throw new BizException(ErrorCode.MOBILE_AUTH_INVALID);
            }
            if (!Boolean.TRUE.equals(user.getPhoneVerified())) {
                user.setPhoneVerified(true);
                user.setPhoneVerifiedAt(OffsetDateTime.now(ZoneOffset.UTC));
                userMapper.updateById(user);
            }
        }

        if (isMobileLoginBlocked(user)) {
            auditService.recordFailure(user.getId(), AuditActionType.MOBILE_LOGIN, "USER",
                    String.valueOf(user.getId()), "user inactive");
            throw new BizException(ErrorCode.MOBILE_AUTH_INVALID);
        }
        if (isNewUser && !Boolean.TRUE.equals(user.getPhoneVerified())) {
            user.setPhoneVerified(true);
            user.setPhoneVerifiedAt(OffsetDateTime.now(ZoneOffset.UTC));
            userMapper.updateById(user);
        }

        Long authUserId = jwtTokenProvider.getUserId(tokenResponse.getAccessToken());
        syncAuthUserId(user, authUserId);

        auditService.recordSuccess(user.getId(), AuditActionType.MOBILE_LOGIN, "USER",
                String.valueOf(user.getId()),
                "mobile login phone=" + maskedPhone + ", newUser=" + isNewUser
                        + ", provider=auth-gateway");
        loginSessionRecorder.record(user.getId(), tokenResponse.getAccessToken(),
                request.isRememberMe(), currentRequest());
        return buildTokenResponse(user, isNewUser, tokenResponse, request.isRememberMe());
    }

    /** 短信登录准入：ACTIVE、CANCELLING（冷静期，需能登录以撤销注销）放行；BANNED 等拒绝。 */
    private boolean isMobileLoginBlocked(UserEntity user) {
        String status = user.getStatus();
        return !"ACTIVE".equalsIgnoreCase(status) && !"CANCELLING".equalsIgnoreCase(status);
    }

    private MobileUserLookup findOrCreateMobileUser(String phone) {
        Object lock = MOBILE_REGISTER_LOCKS.computeIfAbsent(phone, ignored -> new Object());
        synchronized (lock) {
            return findOrCreateMobileUserLocked(phone);
        }
    }

    private MobileUserLookup findOrCreateMobileUserLocked(String phone) {
        UserEntity existing = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getPhone, phone)
                .last("LIMIT 1"));
        if (existing != null) {
            return new MobileUserLookup(existing, false);
        }
        for (int attempt = 0; attempt < MAX_REGISTER_ATTEMPTS; attempt++) {
            UserEntity candidate = buildMobileUser(phone);
            try {
                userMapper.insert(candidate);
                UserProfileEntity profile = new UserProfileEntity();
                profile.setUserId(candidate.getId());
                userProfileMapper.insert(profile);
                return new MobileUserLookup(candidate, true);
            } catch (DataIntegrityViolationException ex) {
                existing = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getPhone, phone)
                        .last("LIMIT 1"));
                if (existing != null) {
                    log.info("Mobile auto-register resolved concurrent phone conflict, phone={}",
                            PhoneSupport.maskPhone(phone));
                    return new MobileUserLookup(existing, false);
                }
                log.warn("Mobile auto-register username conflict, retry={}, phone={}",
                        attempt + 1, PhoneSupport.maskPhone(phone));
            }
        }
        throw new BizException(ErrorCode.INTERNAL_ERROR);
    }

    private record MobileUserLookup(UserEntity user, boolean newlyCreated) {}

    private UserEntity buildMobileUser(String phone) {
        UserEntity user = new UserEntity();
        user.setPhone(phone);
        user.setPhoneVerified(true);
        user.setPhoneVerifiedAt(OffsetDateTime.now(ZoneOffset.UTC));
        user.setPasswordHash(null);
        user.setRole("USER");
        user.setStatus("ACTIVE");
        user.setDisplayName(PhoneSupport.generateDisplayName(phone));
        user.setUsername(PhoneSupport.generateUsername(phone));
        return user;
    }

    private AuthTokenResponse buildTokenResponse(UserEntity user, boolean isNewUser, AuthGatewayClient.TokenResponse tokenResponse, boolean rememberMe) {
        cookieSupport.writeRefreshCookie(tokenResponse.getRefreshToken(), rememberMe);
        boolean onboardingDone = user.getOnboardingCompletedAt() != null;
        return AuthTokenResponse.builder()
                .token(tokenResponse.getAccessToken())
                .tokenType(StringUtils.hasText(tokenResponse.getTokenType()) ? tokenResponse.getTokenType() : "Bearer")
                .expiresIn(tokenResponse.getExpiresIn())
                .isNewUser(isNewUser)
                .onboardingCompleted(onboardingDone)
                .user(AuthTokenResponse.UserInfo.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .role(user.getRole())
                        .build())
                .build();
    }

    private void syncAuthUserId(UserEntity user, Long authUserId) {
        if (authUserId == null || authUserId.equals(user.getAuthUserId())) {
            return;
        }
        user.setAuthUserId(authUserId);
        userMapper.updateById(user);
    }

    private SmsScene resolveScene(String sceneValue) {
        SmsScene scene = SmsScene.fromValue(sceneValue);
        if (scene != SmsScene.MOBILE_LOGIN) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "scene不支持");
        }
        return scene;
    }

    private boolean requiresCaptcha(SmsSendRequest request) {
        return StringUtils.hasText(request.getRiskLevel()) && "high".equalsIgnoreCase(request.getRiskLevel());
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
