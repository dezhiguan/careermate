package com.careermate.auth.sms;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.common.api.ErrorCode;
import com.careermate.common.exception.BizException;
import com.careermate.mapper.UserMapper;
import com.careermate.model.entity.UserEntity;
import com.careermate.security.SecurityProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = true)
class MobileAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SmsCodeStore smsCodeStore;

    @Autowired
    private SecurityProperties securityProperties;

    @Autowired
    private AliyunSmsProperties smsProperties;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private MobileSmsAuthProvider mobileSmsAuthProvider;

    private final AtomicReference<String> lastOutId = new AtomicReference<>("test-out-id");

    @BeforeEach
    void setUp() {
        if (smsCodeStore instanceof InMemorySmsCodeStore inMemoryStore) {
            inMemoryStore.clearForTests();
        }
        reset(mobileSmsAuthProvider);
        lastOutId.set("test-out-id-" + System.nanoTime());
        stubDefaultProviderBehavior();
    }

    private void stubDefaultProviderBehavior() {
        when(mobileSmsAuthProvider.sendVerifyCode(any())).thenAnswer(invocation ->
                MobileSmsAuthProvider.SendResult.builder()
                        .success(true)
                        .outId(lastOutId.get())
                        .providerRequestId("mock-send-req")
                        .providerCode("OK")
                        .build());
        when(mobileSmsAuthProvider.checkVerifyCode(any())).thenAnswer(invocation -> {
            MobileSmsAuthProvider.VerifyRequest req = invocation.getArgument(0, MobileSmsAuthProvider.VerifyRequest.class);
            if (req == null) {
                return MobileSmsAuthProvider.VerifyResult.builder()
                        .success(false)
                        .verifyResult("UNKNOWN")
                        .providerCode("ERROR")
                        .build();
            }
            boolean pass = "123456".equals(req.getVerifyCode());
            return MobileSmsAuthProvider.VerifyResult.builder()
                    .success(pass)
                    .phone(req.getPhone())
                    .verifyResult(pass ? "PASS" : "UNKNOWN")
                    .providerRequestId("mock-verify-req")
                    .providerCode("OK")
                    .build();
        });
    }

    @Test
    void sendSmsCodeSuccessReturnsChallengeId() throws Exception {
        String phone = uniquePhone();
        MvcResult result = mockMvc.perform(post("/api/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone,
                                "scene", "mobile_login"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.challengeId").isNotEmpty())
                .andExpect(jsonPath("$.data.cooldownSeconds").isNumber())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("123456");
    }

    @Test
    void sendSmsCodeWithoutProviderOutIdReturnsServerChallengeId() throws Exception {
        when(mobileSmsAuthProvider.sendVerifyCode(any())).thenReturn(
                MobileSmsAuthProvider.SendResult.builder()
                        .success(true)
                        .outId(null)
                        .providerRequestId("mock-send-req")
                        .providerCode("OK")
                        .build());
        String phone = uniquePhone();
        MvcResult result = mockMvc.perform(post("/api/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone,
                                "scene", "mobile_login"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.challengeId").isNotEmpty())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.path("data").path("outId").isNull()).isTrue();
        assertThat(json.path("data").path("challengeId").asText()).isNotBlank();
    }

    @Test
    void sendSmsCodeCooldownBlocksSecondSend() throws Exception {
        String phone = uniquePhone();
        String body = objectMapper.writeValueAsString(Map.of("phone", phone, "scene", "mobile_login"));
        mockMvc.perform(post("/api/auth/sms/send").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/sms/send").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(ErrorCode.SMS_SEND_TOO_FREQUENT.getCode()));
    }

    @Test
    void mobileLoginAutoRegistersNewUser() throws Exception {
        String phone = uniquePhone();
        String challengeId = sendAndGetChallengeId(phone);

        MvcResult result = mockMvc.perform(post("/api/auth/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone,
                                "verifyCode", "123456",
                                "outId", challengeId,
                                "scene", "mobile_login"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.toString()).doesNotContain("123456");

        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getPhone, phone)
                .last("LIMIT 1"));
        assertThat(user).isNotNull();
        assertThat(user.getPhoneVerified()).isTrue();
        assertThat(user.getPasswordHash()).isNull();
    }

    @Test
    void mobileLoginExistingPhoneUser() throws Exception {
        String phone = uniquePhone();
        UserEntity existing = new UserEntity();
        existing.setUsername("existing_" + System.nanoTime());
        existing.setPhone(phone);
        existing.setPhoneVerified(true);
        existing.setPasswordHash(passwordEncoder.encode("Test123456!"));
        existing.setRole("USER");
        existing.setStatus("ACTIVE");
        userMapper.insert(existing);

        String challengeId = sendAndGetChallengeId(phone);
        mockMvc.perform(post("/api/auth/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone,
                                "verifyCode", "123456",
                                "outId", challengeId,
                                "scene", "mobile_login"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(false))
                .andExpect(jsonPath("$.data.user.userId").value(existing.getId()));
    }

    @Test
    void mobileLoginRejectsMissingChallengeId() throws Exception {
        String phone = uniquePhone();
        sendAndGetChallengeId(phone);
        mockMvc.perform(post("/api/auth/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone,
                                "verifyCode", "123456",
                                "scene", "mobile_login"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.MOBILE_AUTH_CHALLENGE_REQUIRED.getCode()));
    }

    @Test
    void mobileLoginRejectsWrongVerifyCode() throws Exception {
        String phone = uniquePhone();
        String challengeId = sendAndGetChallengeId(phone);
        mockMvc.perform(post("/api/auth/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone,
                                "verifyCode", "000000",
                                "outId", challengeId,
                                "scene", "mobile_login"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.MOBILE_AUTH_CODE_WRONG.getCode()));
    }

    @Test
    void mobileLoginRejectsMismatchedOutId() throws Exception {
        String phone = uniquePhone();
        sendAndGetChallengeId(phone);
        mockMvc.perform(post("/api/auth/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone,
                                "verifyCode", "123456",
                                "outId", "wrong-out-id",
                                "scene", "mobile_login"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.MOBILE_AUTH_EXPIRED.getCode()));
    }

    @Test
    void mobileLoginRejectsOutIdReplay() throws Exception {
        String phone = uniquePhone();
        String challengeId = sendAndGetChallengeId(phone);
        String body = objectMapper.writeValueAsString(Map.of(
                "phone", phone,
                "verifyCode", "123456",
                "outId", challengeId,
                "scene", "mobile_login"
        ));
        mockMvc.perform(post("/api/auth/mobile/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/mobile/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.MOBILE_AUTH_EXPIRED.getCode()));
    }

    @Test
    void mobileLoginRejectsPhoneMismatchWithProvider() throws Exception {
        String phone = uniquePhone();
        String challengeId = sendAndGetChallengeId(phone);
        doReturn(MobileSmsAuthProvider.VerifyResult.builder()
                        .success(true)
                        .phone("13900139000")
                        .verifyResult("PASS")
                        .providerRequestId("mock")
                        .providerCode("OK")
                        .build())
                .when(mobileSmsAuthProvider).checkVerifyCode(any());
        mockMvc.perform(post("/api/auth/mobile/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone,
                                "verifyCode", "123456",
                                "outId", challengeId,
                                "scene", "mobile_login"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.MOBILE_AUTH_INVALID.getCode()));
    }

    @Test
    void sendProviderFailureReturnsSmsProviderError() throws Exception {
        when(mobileSmsAuthProvider.sendVerifyCode(any()))
                .thenReturn(MobileSmsAuthProvider.SendResult.builder()
                        .success(false)
                        .providerCode("ERROR")
                        .build());
        mockMvc.perform(post("/api/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", uniquePhone(),
                                "scene", "mobile_login"
                        ))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.SMS_PROVIDER_ERROR.getCode()));
    }

    @Test
    void loginRateLimitBlocksTooManyAttempts() throws Exception {
        doThrow(new BizException(ErrorCode.MOBILE_AUTH_PROVIDER_ERROR))
                .when(mobileSmsAuthProvider).checkVerifyCode(any());
        String phone = uniquePhone();
        String challengeId = sendAndGetChallengeId(phone);
        String body = objectMapper.writeValueAsString(Map.of(
                "phone", phone,
                "verifyCode", "123456",
                "outId", challengeId,
                "scene", "mobile_login"
        ));
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/auth/mobile/login").contentType(MediaType.APPLICATION_JSON).content(body));
        }
        mockMvc.perform(post("/api/auth/mobile/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(ErrorCode.MOBILE_AUTH_LIMITED.getCode()));
    }

    @Test
    void concurrentMobileLoginAutoRegisterDoesNotReturn500() throws Exception {
        String phone = uniquePhone();
        String challengeId = sendAndGetChallengeId(phone);
        String body = objectMapper.writeValueAsString(Map.of(
                "phone", phone,
                "verifyCode", "123456",
                "outId", challengeId,
                "scene", "mobile_login"
        ));

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger serverErrors = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger successes = new java.util.concurrent.atomic.AtomicInteger();

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    int status = mockMvc.perform(post("/api/auth/mobile/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                    if (status >= 500) {
                        serverErrors.incrementAndGet();
                    } else if (status == 200) {
                        successes.incrementAndGet();
                    }
                } catch (Exception ex) {
                    serverErrors.incrementAndGet();
                }
            });
        }
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

        assertThat(serverErrors.get()).isZero();
        assertThat(successes.get()).isGreaterThanOrEqualTo(1);
        assertThat(userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getPhone, phone))).isEqualTo(1);
    }

    private String sendAndGetChallengeId(String phone) throws Exception {
        MvcResult sendResult = mockMvc.perform(post("/api/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone,
                                "scene", "mobile_login"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(sendResult.getResponse().getContentAsString());
        return json.path("data").path("challengeId").asText();
    }

    private String uniquePhone() {
        long suffix = System.nanoTime() % 1_0000_0000L;
        return "138" + String.format("%08d", suffix).substring(0, 8);
    }
}
