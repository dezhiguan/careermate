package com.careermate.auth.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@Profile("!prod")
@ConditionalOnMissingBean(AliyunPnvsSmsAuthProvider.class)
public class MockPnvsSmsAuthProvider implements MobileSmsAuthProvider {

    private static final String MOCK_CODE = "123456";

    @Override
    public SendResult sendVerifyCode(SendRequest request) {
        log.info("Mock PNVS send, phone={}", PhoneSupport.maskPhone(request.getPhone()));
        return SendResult.builder()
                .success(true)
                .outId("mock-" + UUID.randomUUID())
                .providerRequestId("mock-send")
                .providerCode("OK")
                .build();
    }

    @Override
    public VerifyResult checkVerifyCode(VerifyRequest request) {
        log.info("Mock PNVS verify, phone={}", PhoneSupport.maskPhone(request.getPhone()));
        boolean passed = MOCK_CODE.equals(request.getVerifyCode());
        return VerifyResult.builder()
                .success(passed)
                .phone(request.getPhone())
                .verifyResult(passed ? "PASS" : "UNKNOWN")
                .providerRequestId("mock-verify")
                .providerCode("OK")
                .build();
    }
}
