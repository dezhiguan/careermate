package com.careermate.auth.sms;

import com.careermate.common.api.ErrorCode;
import com.careermate.common.exception.BizException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Component
public class TokenReplayGuard {

    private static final Duration USED_OUT_ID_TTL = Duration.ofMinutes(5);

    private final SmsCodeStore store;

    public TokenReplayGuard(SmsCodeStore store) {
        this.store = store;
    }

    public void assertChallengeNotUsed(SmsScene scene, String challengeHash) {
        if (!StringUtils.hasText(challengeHash)) {
            throw new BizException(ErrorCode.MOBILE_AUTH_INVALID);
        }
        if (store.getValue(usedKey(scene, challengeHash)).isPresent()) {
            throw new BizException(ErrorCode.MOBILE_AUTH_EXPIRED);
        }
    }

    public void markChallengeUsed(SmsScene scene, String challengeHash) {
        if (!StringUtils.hasText(challengeHash)) {
            throw new BizException(ErrorCode.MOBILE_AUTH_INVALID);
        }
        store.setValue(usedKey(scene, challengeHash), "1", USED_OUT_ID_TTL);
    }

    /** @deprecated use {@link #assertChallengeNotUsed} */
    @Deprecated
    public void assertOutIdNotUsed(SmsScene scene, String outIdHash) {
        assertChallengeNotUsed(scene, outIdHash);
    }

    /** @deprecated use {@link #markChallengeUsed} */
    @Deprecated
    public void markOutIdUsed(SmsScene scene, String outIdHash) {
        markChallengeUsed(scene, outIdHash);
    }

    public static String hashOutId(String outId, String pepper) {
        if (!StringUtils.hasText(outId)) {
            return "";
        }
        return PhoneSupport.hashCode(outId, pepper);
    }

    private String usedKey(SmsScene scene, String outIdHash) {
        return "mobile-auth:used:outId:" + scene.value() + ":" + outIdHash;
    }
}
