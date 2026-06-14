package com.careermate.auth.sms;

import com.careermate.common.api.ErrorCode;
import com.careermate.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmsAuthRateLimiterTest {

    private SmsCodeStore store;
    private SmsAuthRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        store = new InMemorySmsCodeStore();
        rateLimiter = new SmsAuthRateLimiter(store);
    }

    @Test
    void recordSendSetsCooldown() {
        String phoneHash = "phone-hash";
        String ipHash = "ip-hash";
        rateLimiter.recordSend(SmsScene.MOBILE_LOGIN, phoneHash, ipHash);
        BizException ex = assertThrows(BizException.class, () ->
                rateLimiter.checkSendAllowed(SmsScene.MOBILE_LOGIN, phoneHash, ipHash, "138****8000", "trace"));
        assertEquals(ErrorCode.SMS_SEND_TOO_FREQUENT.getCode(), ex.getCode());
    }

    @Test
    void loginAttemptLimitBlocksLogin() {
        String phoneHash = "phone-hash";
        store.setValue("mobile-auth:login:attempt:mobile_login:" + phoneHash, "10", Duration.ofMinutes(10));
        BizException ex = assertThrows(BizException.class, () ->
                rateLimiter.checkLoginAllowed(SmsScene.MOBILE_LOGIN, phoneHash, "ip", "138****8000", "trace"));
        assertEquals(ErrorCode.MOBILE_AUTH_LIMITED.getCode(), ex.getCode());
    }

    @Test
    void pendingChallengeMatchesStoredHash() {
        String phoneHash = "phone-hash";
        String challengeHash = "challenge-hash";
        rateLimiter.storePendingChallenge(SmsScene.MOBILE_LOGIN, phoneHash, challengeHash, "provider-out-id");
        assertTrue(rateLimiter.matchesPendingChallenge(SmsScene.MOBILE_LOGIN, phoneHash, challengeHash));
        assertTrue(rateLimiter.getPendingProviderOutId(SmsScene.MOBILE_LOGIN, phoneHash)
                .filter("provider-out-id"::equals)
                .isPresent());
    }
}
