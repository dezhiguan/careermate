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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
public class MobileAuthService {

    private static final long DEFAULT_COOLDOWN_SECONDS = 60L;
    private static final int MAX_REGISTER_ATTEMPTS = 5;

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
            AuthGatewayCookieSupport cookieSupport
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
    }

    public SmsSendResponse sendCode(SmsSendRequest request) {
        SmsScene scene = resolveScene(request.getScene());
        String phone = PhoneSupport.normalizePhone(request.getPhone());
        PhoneSupport.validateMainlandPhone(phone);
        String traceId = currentTraceId();
        String maskedPhone = PhoneSupport.maskPhone(phone);
        String phoneHash = phoneHash(phone);
        String ipHash = ipHash(resolveClientIp());

        if (requiresCaptcha(request)) {
            throw new BizException(ErrorCode.SMS_CAPTCHA_REQUIRED);
        }

        smsAuthRateLimiter.checkSendAllowed(scene, phoneHash, ipHash, maskedPhone, traceId);

        authGatewayClient.sendSms(phone, "login");
        String providerOutId = null;
        String challengeId = UUID.randomUUID().toString();
        String challengeHash = TokenReplayGuard.hashOutId(challengeId, pepper());
        smsAuthRateLimiter.storePendingChallenge(scene, phoneHash, challengeHash, providerOutId);

        smsAuthRateLimiter.recordSend(scene, phoneHash, ipHash);
        auditService.recordSuccess(null, AuditActionType.SMS_SEND, "SMS", scene.value(),
                "send phone=" + maskedPhone + ", provider=auth-gateway");
        log.info("Mobile auth send success, phone={}, scene={}, traceId={}, provider=auth-gateway",
                maskedPhone, scene.value(), traceId);

        long cooldown = smsAuthRateLimiter.sendCooldownRemainingSeconds(scene, phoneHash);
        return SmsSendResponse.builder()
                .cooldownSeconds(cooldown > 0 ? cooldown : DEFAULT_COOLDOWN_SECONDS)
                .challengeId(challengeId)
                .outId(providerOutId)
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
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

        AuthGatewayClient.TokenResponse tokenResponse = authGatewayClient.loginMobile(phone, verifyCode);
        smsAuthRateLimiter.clearLoginFailure(scene, phoneHash);

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
            if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
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

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            auditService.recordFailure(user.getId(), AuditActionType.MOBILE_LOGIN, "USER",
                    String.valueOf(user.getId()), "user inactive");
            throw new BizException(ErrorCode.MOBILE_AUTH_INVALID);
        }
        if (isNewUser && !Boolean.TRUE.equals(user.getPhoneVerified())) {
            user.setPhoneVerified(true);
            user.setPhoneVerifiedAt(OffsetDateTime.now(ZoneOffset.UTC));
            userMapper.updateById(user);
        }

        auditService.recordSuccess(user.getId(), AuditActionType.MOBILE_LOGIN, "USER",
                String.valueOf(user.getId()),
                "mobile login phone=" + maskedPhone + ", newUser=" + isNewUser
                        + ", provider=auth-gateway");
        return buildTokenResponse(user, isNewUser, tokenResponse);
    }

    private MobileUserLookup findOrCreateMobileUser(String phone) {
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

    private AuthTokenResponse buildTokenResponse(UserEntity user, boolean isNewUser, AuthGatewayClient.TokenResponse tokenResponse) {
        cookieSupport.writeRefreshCookie(tokenResponse.getRefreshToken());
        Long tokenUserId = jwtTokenProvider.getUserId(tokenResponse.getAccessToken());
        return AuthTokenResponse.builder()
                .token(tokenResponse.getAccessToken())
                .tokenType(StringUtils.hasText(tokenResponse.getTokenType()) ? tokenResponse.getTokenType() : "Bearer")
                .expiresIn(tokenResponse.getExpiresIn())
                .isNewUser(isNewUser)
                .user(AuthTokenResponse.UserInfo.builder()
                        .userId(tokenUserId)
                        .username(user.getUsername())
                        .role(user.getRole())
                        .build())
                .build();
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
