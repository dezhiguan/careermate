package com.careermate.auth.sms;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.common.api.ErrorCode;
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
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = true)
class PasswordResetIntegrationTest {

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
    void sendPasswordResetSmsSuccessReturnsChallengeId() throws Exception {
        String phone = uniquePhone();
        createActiveUser(phone, "OldPass123!");

        MvcResult result = mockMvc.perform(post("/api/auth/password-reset/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", phone))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.challengeId").isNotEmpty())
                .andExpect(jsonPath("$.data.cooldownSeconds").isNumber())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("123456");
    }

    @Test
    void sendPasswordResetSmsForUnknownPhoneDoesNotExposeUserNotFound() throws Exception {
        String phone = uniquePhone();
        mockMvc.perform(post("/api/auth/password-reset/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", phone))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.challengeId").isNotEmpty())
                .andExpect(jsonPath("$.data.cooldownSeconds").isNumber());
    }

    @Test
    void confirmWithoutSendFails() throws Exception {
        String phone = uniquePhone();
        createActiveUser(phone, "OldPass123!");

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone,
                                "verifyCode", "123456",
                                "challengeId", "missing-challenge",
                                "newPassword", "NewPass123!"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PASSWORD_RESET_INVALID.getCode()));
    }

    @Test
    void confirmWithMismatchedChallengeIdFails() throws Exception {
        String phone = uniquePhone();
        createActiveUser(phone, "OldPass123!");
        sendPasswordResetSms(phone);

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone,
                                "verifyCode", "123456",
                                "challengeId", "wrong-challenge",
                                "newPassword", "NewPass123!"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PASSWORD_RESET_INVALID.getCode()));
    }

    @Test
    void confirmWithWrongVerifyCodeFails() throws Exception {
        String phone = uniquePhone();
        createActiveUser(phone, "OldPass123!");
        String challengeId = sendAndGetChallengeId(phone);

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone,
                                "verifyCode", "000000",
                                "challengeId", challengeId,
                                "newPassword", "NewPass123!"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PASSWORD_RESET_INVALID.getCode()));
    }

    @Test
    void confirmRejectsChallengeIdReplay() throws Exception {
        String phone = uniquePhone();
        createActiveUser(phone, "OldPass123!");
        String challengeId = sendAndGetChallengeId(phone);
        String body = objectMapper.writeValueAsString(Map.of(
                "phone", phone,
                "verifyCode", "123456",
                "challengeId", challengeId,
                "newPassword", "NewPass123!"
        ));

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PASSWORD_RESET_INVALID.getCode()));
    }

    @Test
    void activeUserCanLoginWithNewPasswordAfterReset() throws Exception {
        String phone = uniquePhone();
        String oldPassword = "OldPass123!";
        String newPassword = "NewPass123!";
        UserEntity user = createActiveUser(phone, oldPassword);
        String challengeId = sendAndGetChallengeId(phone);

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone,
                                "verifyCode", "123456",
                                "challengeId", challengeId,
                                "newPassword", newPassword
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", user.getUsername(),
                                "password", oldPassword
                        ))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", user.getUsername(),
                                "password", newPassword
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    void inactiveUserDoesNotExposeStatus() throws Exception {
        String phone = uniquePhone();
        UserEntity user = createActiveUser(phone, "OldPass123!");
        user.setStatus("INACTIVE");
        userMapper.updateById(user);
        String challengeId = sendAndGetChallengeId(phone);

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone,
                                "verifyCode", "123456",
                                "challengeId", challengeId,
                                "newPassword", "NewPass123!"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PASSWORD_RESET_INVALID.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.PASSWORD_RESET_INVALID.getMessage()));
    }

    @Test
    void mobileLoginChallengeIdCannotBeUsedForPasswordReset() throws Exception {
        String phone = uniquePhone();
        createActiveUser(phone, "OldPass123!");

        MvcResult sendResult = mockMvc.perform(post("/api/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone,
                                "scene", "mobile_login"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(sendResult.getResponse().getContentAsString());
        String mobileLoginChallengeId = json.path("data").path("challengeId").asText();

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone,
                                "verifyCode", "123456",
                                "challengeId", mobileLoginChallengeId,
                                "newPassword", "NewPass123!"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PASSWORD_RESET_INVALID.getCode()));
    }

    private UserEntity createActiveUser(String phone, String password) {
        UserEntity user = new UserEntity();
        user.setUsername("user_" + System.nanoTime());
        user.setPhone(phone);
        user.setPhoneVerified(true);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole("USER");
        user.setStatus("ACTIVE");
        userMapper.insert(user);
        return user;
    }

    private void sendPasswordResetSms(String phone) throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", phone))))
                .andExpect(status().isOk());
    }

    private String sendAndGetChallengeId(String phone) throws Exception {
        MvcResult sendResult = mockMvc.perform(post("/api/auth/password-reset/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", phone))))
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
