package com.careermate.auth.sms;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeResponse;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeResponseBody;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponseBody;
import com.aliyun.teaopenapi.models.Config;
import com.careermate.common.api.ErrorCode;
import com.careermate.common.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "aliyun.sms", name = "enabled", havingValue = "true")
public class AliyunPnvsSmsAuthProvider implements MobileSmsAuthProvider {

    private static final int CODE_VALID_SECONDS = 300;
    private static final int CODE_LENGTH = 6;
    private static final String VERIFY_PASS = "PASS";
    private static final String MOCK_CODE = "123456";

    private final AliyunSmsProperties properties;
    private final ObjectMapper objectMapper;

    public AliyunPnvsSmsAuthProvider(AliyunSmsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public SendResult sendVerifyCode(SendRequest request) {
        if (properties.isMockEnabled()) {
            log.info("Aliyun PNVS mock send, phone={}", PhoneSupport.maskPhone(request.getPhone()));
            return SendResult.builder()
                    .success(true)
                    .outId("mock-" + UUID.randomUUID())
                    .providerRequestId("mock-send-request-id")
                    .providerCode("OK")
                    .build();
        }
        validateConfig(ErrorCode.SMS_PROVIDER_ERROR);
        try {
            Client client = createClient();
            String templateParam = objectMapper.writeValueAsString(Map.of(
                    "code", "##code##",
                    "min", String.valueOf(properties.getTemplate().getValidMinutes())
            ));
            SendSmsVerifyCodeRequest sendRequest = new SendSmsVerifyCodeRequest()
                    .setPhoneNumber(request.getPhone())
                    .setSignName(properties.getSignName())
                    .setTemplateCode(properties.getTemplate().getLogin())
                    .setTemplateParam(templateParam)
                    .setValidTime((long) CODE_VALID_SECONDS)
                    .setCodeLength((long) CODE_LENGTH)
                    .setCodeType(1L)
                    .setReturnVerifyCode(false);
            SendSmsVerifyCodeResponse response = client.sendSmsVerifyCode(sendRequest);
            SendSmsVerifyCodeResponseBody body = response.getBody();
            return mapSendResult(body, request.getPhone());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Aliyun PNVS send exception, phone={}", PhoneSupport.maskPhone(request.getPhone()), e);
            throw new BizException(ErrorCode.SMS_PROVIDER_ERROR);
        }
    }

    @Override
    public VerifyResult checkVerifyCode(VerifyRequest request) {
        if (properties.isMockEnabled()) {
            log.info("Aliyun PNVS mock verify, phone={}", PhoneSupport.maskPhone(request.getPhone()));
            boolean passed = MOCK_CODE.equals(request.getVerifyCode());
            return VerifyResult.builder()
                    .success(passed)
                    .phone(request.getPhone())
                    .verifyResult(passed ? VERIFY_PASS : "UNKNOWN")
                    .providerRequestId("mock-verify-request-id")
                    .providerCode("OK")
                    .build();
        }
        validateConfig(ErrorCode.MOBILE_AUTH_PROVIDER_ERROR);
        try {
            Client client = createClient();
            CheckSmsVerifyCodeRequest checkRequest = new CheckSmsVerifyCodeRequest()
                    .setPhoneNumber(request.getPhone())
                    .setVerifyCode(request.getVerifyCode());
            if (StringUtils.hasText(request.getOutId())) {
                checkRequest.setOutId(request.getOutId());
            }
            CheckSmsVerifyCodeResponse response = client.checkSmsVerifyCode(checkRequest);
            CheckSmsVerifyCodeResponseBody body = response.getBody();
            String requestId = resolveRequestId(response);
            return mapVerifyResult(body, request.getPhone(), requestId);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Aliyun PNVS verify exception, phone={}", PhoneSupport.maskPhone(request.getPhone()), e);
            throw new BizException(ErrorCode.MOBILE_AUTH_PROVIDER_ERROR);
        }
    }

    private SendResult mapSendResult(SendSmsVerifyCodeResponseBody body, String phone) {
        String responseCode = body == null ? null : body.getCode();
        String requestId = body == null ? null : body.getRequestId();
        String message = body == null ? null : body.getMessage();
        Boolean success = body == null ? null : body.getSuccess();
        String outId = body != null && body.getModel() != null ? body.getModel().getOutId() : null;
        if (!Boolean.TRUE.equals(success) || !"OK".equalsIgnoreCase(responseCode)) {
            log.error("Aliyun PNVS send failed, requestId={}, code={}, message={}, phone={}",
                    requestId, responseCode, message, PhoneSupport.maskPhone(phone));
            throw new BizException(ErrorCode.SMS_PROVIDER_ERROR);
        }
        log.info("Aliyun PNVS send ok, requestId={}, phone={}, outIdPresent={}",
                requestId, PhoneSupport.maskPhone(phone), StringUtils.hasText(outId));
        return SendResult.builder()
                .success(true)
                .outId(outId)
                .providerRequestId(requestId)
                .providerCode(responseCode)
                .providerMessage(message)
                .build();
    }

    private VerifyResult mapVerifyResult(CheckSmsVerifyCodeResponseBody body, String phone, String requestId) {
        String responseCode = body == null ? null : body.getCode();
        String message = body == null ? null : body.getMessage();
        Boolean success = body == null ? null : body.getSuccess();
        String verifyResult = body != null && body.getModel() != null ? body.getModel().getVerifyResult() : null;
        boolean passed = Boolean.TRUE.equals(success)
                && "OK".equalsIgnoreCase(responseCode)
                && VERIFY_PASS.equalsIgnoreCase(verifyResult);
        if (!passed) {
            log.warn("Aliyun PNVS verify not pass, requestId={}, code={}, verifyResult={}, phone={}",
                    requestId, responseCode, verifyResult, PhoneSupport.maskPhone(phone));
        } else {
            log.info("Aliyun PNVS verify pass, requestId={}, phone={}", requestId, PhoneSupport.maskPhone(phone));
        }
        return VerifyResult.builder()
                .success(passed)
                .phone(phone)
                .verifyResult(verifyResult)
                .providerRequestId(requestId)
                .providerCode(responseCode)
                .providerMessage(message)
                .build();
    }

    private String resolveRequestId(CheckSmsVerifyCodeResponse response) {
        if (response == null || response.getHeaders() == null) {
            return null;
        }
        return response.getHeaders().get("x-acs-request-id");
    }

    private void validateConfig(ErrorCode errorCode) {
        if (!StringUtils.hasText(properties.getAccessKeyId())
                || !StringUtils.hasText(properties.getAccessKeySecret())
                || !StringUtils.hasText(properties.getSignName())
                || !StringUtils.hasText(properties.getTemplate().getLogin())) {
            log.error("Aliyun PNVS config incomplete");
            throw new BizException(errorCode);
        }
    }

    private Client createClient() throws Exception {
        String endpoint = StringUtils.hasText(properties.getEndpoint())
                ? properties.getEndpoint()
                : "dypnsapi.aliyuncs.com";
        Config config = new Config()
                .setAccessKeyId(properties.getAccessKeyId())
                .setAccessKeySecret(properties.getAccessKeySecret())
                .setEndpoint(endpoint)
                .setRegionId(properties.getRegion());
        return new Client(config);
    }
}
