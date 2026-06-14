package com.careermate.auth.sms;

import com.careermate.common.api.ErrorCode;
import com.careermate.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhoneSupportTest {

    @Test
    void validateMainlandPhoneAcceptsValidNumber() {
        PhoneSupport.validateMainlandPhone("13800138000");
    }

    @Test
    void validateMainlandPhoneRejectsInvalidNumber() {
        BizException ex = assertThrows(BizException.class, () -> PhoneSupport.validateMainlandPhone("23800138000"));
        assertEquals(ErrorCode.PHONE_FORMAT_INVALID.getCode(), ex.getCode());
    }

    @Test
    void maskPhoneDoesNotRevealFullNumber() {
        assertEquals("138****8000", PhoneSupport.maskPhone("13800138000"));
    }

    @Test
    void generateUsernameDoesNotContainFullPhone() {
        String username = PhoneSupport.generateUsername("13800138000");
        assertEquals("cm_8000_", username.substring(0, 8));
        assertEquals(12, username.length());
    }
}
