package com.careermate.auth.sms;

import lombok.Builder;
import lombok.Data;

public interface MobileSmsAuthProvider {

    SendResult sendVerifyCode(SendRequest request);

    VerifyResult checkVerifyCode(VerifyRequest request);

    @Data
    @Builder
    class SendRequest {
        private String phone;
        private SmsScene scene;
    }

    @Data
    @Builder
    class SendResult {
        private boolean success;
        private String outId;
        private String providerRequestId;
        private String providerCode;
        private String providerMessage;
    }

    @Data
    @Builder
    class VerifyRequest {
        private String phone;
        private String verifyCode;
        private String outId;
        private SmsScene scene;
    }

    @Data
    @Builder
    class VerifyResult {
        private boolean success;
        private String phone;
        private String providerRequestId;
        private String providerCode;
        private String providerMessage;
        private String verifyResult;
    }
}
