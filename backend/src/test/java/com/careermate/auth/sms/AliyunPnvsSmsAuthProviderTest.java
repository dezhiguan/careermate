package com.careermate.auth.sms;

import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeResponse;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeResponseBody;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeResponseBody.CheckSmsVerifyCodeResponseBodyModel;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponseBody;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponseBody.SendSmsVerifyCodeResponseBodyModel;
import com.careermate.common.api.ErrorCode;
import com.careermate.common.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AliyunPnvsSmsAuthProviderTest {

    private AliyunSmsProperties properties;
    private AliyunPnvsSmsAuthProvider provider;

    @BeforeEach
    void setUp() {
        properties = new AliyunSmsProperties();
        provider = new AliyunPnvsSmsAuthProvider(properties, new ObjectMapper());
    }

    @Test
    void mockSendAndVerifyReturnDeterministicResults() {
        properties.setMockEnabled(true);

        MobileSmsAuthProvider.SendResult send = provider.sendVerifyCode(MobileSmsAuthProvider.SendRequest.builder()
                .phone("13800138000")
                .scene(SmsScene.MOBILE_LOGIN)
                .build());
        assertTrue(send.isSuccess());
        assertTrue(send.getOutId().startsWith("mock-"));
        assertEquals("OK", send.getProviderCode());

        MobileSmsAuthProvider.VerifyResult pass = provider.checkVerifyCode(MobileSmsAuthProvider.VerifyRequest.builder()
                .phone("13800138000")
                .verifyCode("123456")
                .build());
        assertTrue(pass.isSuccess());
        assertEquals("PASS", pass.getVerifyResult());

        MobileSmsAuthProvider.VerifyResult fail = provider.checkVerifyCode(MobileSmsAuthProvider.VerifyRequest.builder()
                .phone("13800138000")
                .verifyCode("000000")
                .build());
        assertFalse(fail.isSuccess());
        assertEquals("UNKNOWN", fail.getVerifyResult());
    }

    @Test
    void missingConfigThrowsSceneSpecificBizException() {
        properties.setMockEnabled(false);

        BizException sendError = assertThrows(BizException.class, () -> provider.sendVerifyCode(
                MobileSmsAuthProvider.SendRequest.builder().phone("13800138000").build()));
        assertEquals(ErrorCode.SMS_PROVIDER_ERROR.getCode(), sendError.getCode());

        BizException verifyError = assertThrows(BizException.class, () -> provider.checkVerifyCode(
                MobileSmsAuthProvider.VerifyRequest.builder()
                        .phone("13800138000")
                        .verifyCode("123456")
                        .build()));
        assertEquals(ErrorCode.MOBILE_AUTH_PROVIDER_ERROR.getCode(), verifyError.getCode());
    }

    @Test
    void mapSendResultReturnsProviderFieldsAndRejectsFailures() throws Exception {
        SendSmsVerifyCodeResponseBody okBody = new SendSmsVerifyCodeResponseBody()
                .setSuccess(true)
                .setCode("OK")
                .setMessage("sent")
                .setRequestId("req-1")
                .setModel(new SendSmsVerifyCodeResponseBodyModel().setOutId("out-1"));

        MobileSmsAuthProvider.SendResult result = invokeMapSendResult(okBody, "13800138000");

        assertTrue(result.isSuccess());
        assertEquals("out-1", result.getOutId());
        assertEquals("req-1", result.getProviderRequestId());
        assertEquals("OK", result.getProviderCode());
        assertEquals("sent", result.getProviderMessage());

        assertThrows(BizException.class, () -> invokeMapSendResult(new SendSmsVerifyCodeResponseBody()
                .setSuccess(false)
                .setCode("LIMIT")
                .setMessage("too many"), "13800138000"));
        assertThrows(BizException.class, () -> invokeMapSendResult(null, "13800138000"));
    }

    @Test
    void mapVerifyResultMapsPassFailAndRequestId() throws Exception {
        CheckSmsVerifyCodeResponseBody passBody = new CheckSmsVerifyCodeResponseBody()
                .setSuccess(true)
                .setCode("OK")
                .setMessage("verified")
                .setModel(new CheckSmsVerifyCodeResponseBodyModel().setVerifyResult("PASS"));

        MobileSmsAuthProvider.VerifyResult pass = invokeMapVerifyResult(passBody, "13800138000", "req-v");

        assertTrue(pass.isSuccess());
        assertEquals("13800138000", pass.getPhone());
        assertEquals("PASS", pass.getVerifyResult());
        assertEquals("req-v", pass.getProviderRequestId());
        assertEquals("OK", pass.getProviderCode());
        assertEquals("verified", pass.getProviderMessage());

        MobileSmsAuthProvider.VerifyResult fail = invokeMapVerifyResult(new CheckSmsVerifyCodeResponseBody()
                .setSuccess(true)
                .setCode("OK")
                .setModel(new CheckSmsVerifyCodeResponseBodyModel().setVerifyResult("FAIL")),
                "13800138000",
                "req-v2");
        assertFalse(fail.isSuccess());
        assertEquals("FAIL", fail.getVerifyResult());

        MobileSmsAuthProvider.VerifyResult empty = invokeMapVerifyResult(null, "13800138000", null);
        assertFalse(empty.isSuccess());
        assertNull(empty.getProviderCode());
    }

    @Test
    void resolveRequestIdReadsAliyunHeader() throws Exception {
        CheckSmsVerifyCodeResponse response = new CheckSmsVerifyCodeResponse()
                .setHeaders(Map.of("x-acs-request-id", "acs-req"));

        assertEquals("acs-req", invokeResolveRequestId(response));
        assertNull(invokeResolveRequestId(new CheckSmsVerifyCodeResponse()));
        assertNull(invokeResolveRequestId(null));
    }

    private MobileSmsAuthProvider.SendResult invokeMapSendResult(
            SendSmsVerifyCodeResponseBody body,
            String phone
    ) throws Exception {
        Method method = AliyunPnvsSmsAuthProvider.class
                .getDeclaredMethod("mapSendResult", SendSmsVerifyCodeResponseBody.class, String.class);
        method.setAccessible(true);
        try {
            return (MobileSmsAuthProvider.SendResult) method.invoke(provider, body, phone);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    private MobileSmsAuthProvider.VerifyResult invokeMapVerifyResult(
            CheckSmsVerifyCodeResponseBody body,
            String phone,
            String requestId
    ) throws Exception {
        Method method = AliyunPnvsSmsAuthProvider.class
                .getDeclaredMethod("mapVerifyResult", CheckSmsVerifyCodeResponseBody.class, String.class, String.class);
        method.setAccessible(true);
        try {
            return (MobileSmsAuthProvider.VerifyResult) method.invoke(provider, body, phone, requestId);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    private String invokeResolveRequestId(CheckSmsVerifyCodeResponse response) throws Exception {
        Method method = AliyunPnvsSmsAuthProvider.class
                .getDeclaredMethod("resolveRequestId", CheckSmsVerifyCodeResponse.class);
        method.setAccessible(true);
        return (String) method.invoke(provider, response);
    }
}
