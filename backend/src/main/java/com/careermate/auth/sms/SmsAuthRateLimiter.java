package com.careermate.auth.sms;

import com.careermate.common.api.ErrorCode;
import com.careermate.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Slf4j
@Component
public class SmsAuthRateLimiter {

    private static final Duration COOLDOWN = Duration.ofSeconds(60);
    private static final Duration ONE_HOUR = Duration.ofHours(1);
    private static final Duration ONE_DAY = Duration.ofDays(1);
    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);
    private static final Duration OUT_ID_TTL = Duration.ofMinutes(5);

    private static final int PHONE_HOUR_SEND_LIMIT = 5;
    private static final int PHONE_DAY_SEND_LIMIT = 10;
    private static final int IP_MINUTE_SEND_LIMIT = 10;
    private static final int IP_HOUR_SEND_LIMIT = 100;
    private static final int PHONE_IP_HOUR_SEND_LIMIT = 5;

    private static final int PHONE_LOGIN_ATTEMPT_LIMIT = 10;
    private static final int IP_MINUTE_LOGIN_LIMIT = 20;
    private static final int IP_HOUR_LOGIN_LIMIT = 200;
    private static final int PHONE_IP_HOUR_LOGIN_LIMIT = 20;
    private static final int LOGIN_FAIL_LOCK_LIMIT = 5;

    private final SmsCodeStore store;

    public SmsAuthRateLimiter(SmsCodeStore store) {
        this.store = store;
    }

    public void checkSendAllowed(SmsScene scene, String phoneHash, String ipHash, String maskedPhone, String traceId) {
        if (store.getValue(key("mobile-auth:send:cooldown", scene, phoneHash)).isPresent()) {
            log.warn("Mobile auth send cooldown, phone={}, traceId={}", maskedPhone, traceId);
            throw new BizException(ErrorCode.SMS_SEND_TOO_FREQUENT);
        }
        assertUnderLimit(store.getCounter(key("mobile-auth:send:hour", scene, phoneHash)), PHONE_HOUR_SEND_LIMIT,
                ErrorCode.SMS_SEND_LIMITED, "send phone hour", maskedPhone, traceId);
        assertUnderLimit(store.getCounter(key("mobile-auth:send:day", scene, phoneHash)), PHONE_DAY_SEND_LIMIT,
                ErrorCode.SMS_SEND_LIMITED, "send phone day", maskedPhone, traceId);
        assertUnderLimit(store.getCounter(key("mobile-auth:send:ip:minute", scene, ipHash)), IP_MINUTE_SEND_LIMIT,
                ErrorCode.SMS_SEND_LIMITED, "send ip minute", ipHash, traceId);
        assertUnderLimit(store.getCounter(key("mobile-auth:send:ip:hour", scene, ipHash)), IP_HOUR_SEND_LIMIT,
                ErrorCode.SMS_SEND_LIMITED, "send ip hour", ipHash, traceId);
        assertUnderLimit(store.getCounter(key("mobile-auth:send:phone_ip:hour", scene, phoneHash, ipHash)),
                PHONE_IP_HOUR_SEND_LIMIT, ErrorCode.SMS_SEND_LIMITED, "send phone+ip hour", maskedPhone, traceId);
    }

    public void recordSend(SmsScene scene, String phoneHash, String ipHash) {
        store.setValue(key("mobile-auth:send:cooldown", scene, phoneHash), "1", COOLDOWN);
        store.increment(key("mobile-auth:send:hour", scene, phoneHash), ONE_HOUR);
        store.increment(key("mobile-auth:send:day", scene, phoneHash), ONE_DAY);
        store.increment(key("mobile-auth:send:ip:minute", scene, ipHash), ONE_MINUTE);
        store.increment(key("mobile-auth:send:ip:hour", scene, ipHash), ONE_HOUR);
        store.increment(key("mobile-auth:send:phone_ip:hour", scene, phoneHash, ipHash), ONE_HOUR);
    }

    public long sendCooldownRemainingSeconds(SmsScene scene, String phoneHash) {
        return store.getRemainingTtlSeconds(key("mobile-auth:send:cooldown", scene, phoneHash)).orElse(0L);
    }

    public void storePendingChallenge(SmsScene scene, String phoneHash, String challengeHash, String providerOutId) {
        store.setValue(key("mobile-auth:pending:challenge", scene, phoneHash), challengeHash, OUT_ID_TTL);
        String providerKey = key("mobile-auth:pending:provider-out-id", scene, phoneHash);
        if (StringUtils.hasText(providerOutId)) {
            store.setValue(providerKey, providerOutId, OUT_ID_TTL);
        } else {
            store.delete(providerKey);
        }
    }

    public boolean matchesPendingChallenge(SmsScene scene, String phoneHash, String challengeHash) {
        return store.getValue(key("mobile-auth:pending:challenge", scene, phoneHash))
                .map(stored -> stored.equals(challengeHash))
                .orElse(false);
    }

    public java.util.Optional<String> getPendingProviderOutId(SmsScene scene, String phoneHash) {
        return store.getValue(key("mobile-auth:pending:provider-out-id", scene, phoneHash));
    }

    /** @deprecated use {@link #storePendingChallenge} */
    @Deprecated
    public void storePendingOutId(SmsScene scene, String phoneHash, String outIdHash) {
        storePendingChallenge(scene, phoneHash, outIdHash, null);
    }

    /** @deprecated use {@link #matchesPendingChallenge} */
    @Deprecated
    public boolean matchesPendingOutId(SmsScene scene, String phoneHash, String outIdHash) {
        return matchesPendingChallenge(scene, phoneHash, outIdHash);
    }

    public void checkLoginAllowed(SmsScene scene, String phoneHash, String ipHash, String maskedPhone, String traceId) {
        assertUnderLimit(store.getCounter(key("mobile-auth:login:attempt", scene, phoneHash)), PHONE_LOGIN_ATTEMPT_LIMIT,
                ErrorCode.MOBILE_AUTH_LIMITED, "login phone attempt", maskedPhone, traceId);
        assertUnderLimit(store.getCounter(key("mobile-auth:login:ip:minute", scene, ipHash)), IP_MINUTE_LOGIN_LIMIT,
                ErrorCode.MOBILE_AUTH_LIMITED, "login ip minute", ipHash, traceId);
        assertUnderLimit(store.getCounter(key("mobile-auth:login:ip:hour", scene, ipHash)), IP_HOUR_LOGIN_LIMIT,
                ErrorCode.MOBILE_AUTH_LIMITED, "login ip hour", ipHash, traceId);
        assertUnderLimit(store.getCounter(key("mobile-auth:login:phone_ip:hour", scene, phoneHash, ipHash)),
                PHONE_IP_HOUR_LOGIN_LIMIT, ErrorCode.MOBILE_AUTH_LIMITED, "login phone+ip hour", maskedPhone, traceId);
        long failCount = store.getCounter(key("mobile-auth:login:fail", scene, phoneHash));
        if (failCount >= LOGIN_FAIL_LOCK_LIMIT) {
            log.warn("Mobile auth login fail lock, phone={}, count={}, traceId={}", maskedPhone, failCount, traceId);
            throw new BizException(ErrorCode.MOBILE_AUTH_TOO_MANY_ATTEMPTS);
        }
    }

    public void recordLoginAttempt(SmsScene scene, String phoneHash, String ipHash) {
        store.increment(key("mobile-auth:login:attempt", scene, phoneHash), TEN_MINUTES);
        store.increment(key("mobile-auth:login:ip:minute", scene, ipHash), ONE_MINUTE);
        store.increment(key("mobile-auth:login:ip:hour", scene, ipHash), ONE_HOUR);
        store.increment(key("mobile-auth:login:phone_ip:hour", scene, phoneHash, ipHash), ONE_HOUR);
    }

    public void recordLoginFailure(SmsScene scene, String phoneHash) {
        store.increment(key("mobile-auth:login:fail", scene, phoneHash), TEN_MINUTES);
    }

    public void clearLoginFailure(SmsScene scene, String phoneHash) {
        store.delete(key("mobile-auth:login:fail", scene, phoneHash));
        store.delete(key("mobile-auth:pending:challenge", scene, phoneHash));
        store.delete(key("mobile-auth:pending:provider-out-id", scene, phoneHash));
    }

    private void assertUnderLimit(long count, int limit, ErrorCode errorCode, String label, String subject, String traceId) {
        if (count >= limit) {
            log.warn("Mobile auth rate limit {}, subject={}, count={}, traceId={}", label, subject, count, traceId);
            throw new BizException(errorCode);
        }
    }

    private String key(String prefix, SmsScene scene, String... parts) {
        StringBuilder builder = new StringBuilder(prefix).append(':').append(scene.value());
        for (String part : parts) {
            builder.append(':').append(part);
        }
        return builder.toString();
    }
}
