package com.careermate.auth.gateway;

import com.careermate.common.api.ErrorCode;
import com.careermate.common.exception.BizException;
import com.careermate.security.ClientAssertionFactory;
import com.careermate.security.SecurityProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class AuthGatewayClient {

    private static final String TOKEN_EXCHANGE_GRANT = "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";

    private final SecurityProperties.AuthGateway properties;
    private final ClientAssertionFactory clientAssertionFactory;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AuthGatewayClient(SecurityProperties securityProperties, ClientAssertionFactory clientAssertionFactory, ObjectMapper objectMapper) {
        this.properties = securityProperties.getAuthGateway();
        this.clientAssertionFactory = clientAssertionFactory;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeoutMs());
        factory.setReadTimeout(properties.getTimeoutMs());
        this.restTemplate = new RestTemplate(factory);
    }

    public TokenResponse loginPassword(String account, String password) {
        MultiValueMap<String, String> form = clientForm();
        form.add("account", account);
        form.add("password", password);
        form.add("target_aud", properties.getAudience());
        return postForm("/auth/login/password", form, TokenResponse.class);
    }

    public TokenResponse loginMobile(String phone, String code) {
        MultiValueMap<String, String> form = clientForm();
        form.add("phone", phone);
        form.add("code", code);
        form.add("target_aud", properties.getAudience());
        return postForm("/auth/login/mobile", form, TokenResponse.class);
    }

    public void sendSms(String phone, String scene) {
        postJson("/auth/sms/send", Map.of("phone", phone, "scene", scene), Map.class);
    }

    public ResetInitResponse resetInit(String account) {
        return postJson("/auth/password/reset/init", Map.of("account", account, "phone", account), ResetInitResponse.class);
    }

    public ResetVerifyResponse resetVerify(String account, String code) {
        return postJson("/auth/password/reset/verify", Map.of("account", account, "phone", account, "code", code), ResetVerifyResponse.class);
    }

    public TokenResponse resetConfirm(String resetTicket, String newPassword) {
        Map<String, Object> body = Map.of(
                "reset_ticket", resetTicket,
                "new_password", newPassword,
                "target_aud", properties.getAudience(),
                "client_id", properties.getClientId(),
                "client_assertion_type", ClientAssertionFactory.ASSERTION_TYPE,
                "client_assertion", clientAssertionFactory.create()
        );
        return postJson("/auth/password/reset/confirm", body, TokenResponse.class);
    }

    public TokenResponse refresh(String refreshToken) {
        MultiValueMap<String, String> form = clientForm();
        form.add("refresh_token", refreshToken);
        return postForm("/auth/token/refresh", form, TokenResponse.class);
    }

    public TokenExchangeResponse tokenExchange(String subjectToken, String requestedAudience, String requestedScopes) {
        MultiValueMap<String, String> form = clientForm();
        form.add("grant_type", TOKEN_EXCHANGE_GRANT);
        form.add("subject_token", subjectToken);
        form.add("subject_token_type", ACCESS_TOKEN_TYPE);
        form.add("requested_audience", requestedAudience);
        form.add("requested_scopes", requestedScopes);
        return postForm("/oauth/token-exchange", form, TokenExchangeResponse.class);
    }

    private MultiValueMap<String, String> clientForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.getClientId());
        form.add("client_assertion_type", ClientAssertionFactory.ASSERTION_TYPE);
        form.add("client_assertion", clientAssertionFactory.create());
        return form;
    }

    private <T> T postForm(String path, MultiValueMap<String, String> form, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return exchange(path, new HttpEntity<>(form, headers), responseType);
    }

    private <T> T postJson(String path, Object body, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return exchange(path, new HttpEntity<>(body, headers), responseType);
    }

    private <T> T exchange(String path, HttpEntity<?> entity, Class<T> responseType) {
        try {
            ResponseEntity<T> response = restTemplate.postForEntity(properties.getBaseUrl() + path, entity, responseType);
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            throw toBizException(ex);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "认证服务不可用");
        }
    }

    private BizException toBizException(HttpStatusCodeException ex) {
        String gatewayCode = null;
        String gatewayMessage = null;
        try {
            Map<String, Object> payload = objectMapper.readValue(ex.getResponseBodyAsString(), new TypeReference<>() {});
            Object code = payload.getOrDefault("error", payload.get("code"));
            Object message = payload.getOrDefault("message", payload.get("msg"));
            gatewayCode = code == null ? null : String.valueOf(code);
            gatewayMessage = message == null ? null : String.valueOf(message);
        } catch (Exception ignored) {
            // keep default mapping below
        }

        String friendlyMessage = friendlyGatewayMessage(gatewayCode, gatewayMessage, ex.getStatusCode().value());
        return new BizException(ex.getStatusCode().value(), friendlyMessage);
    }

    private String friendlyGatewayMessage(String code, String message, int status) {
        if (code == null && message == null) {
            if (status == 429) {
                return "操作过于频繁，请稍后再试";
            }
            if (status == 401) {
                return "账号或密码不正确";
            }
            if (status == 403) {
                return "当前账号没有访问权限，请联系管理员开通";
            }
            return "认证服务暂时不可用，请稍后再试";
        }
        String text = ((code == null ? "" : code) + " " + (message == null ? "" : message)).toUpperCase();
        if (text.contains("SMS_SEND_TOO_FREQUENT")) {
            return "验证码已发送，请稍后再试";
        }
        if (text.contains("SMS_PHONE_DAY_LIMITED")
                || text.contains("SMS_IP_MINUTE_LIMITED")
                || text.contains("SMS_PROVIDER_RATE_LIMITED")
                || status == 429) {
            return "验证码发送过于频繁，请稍后再试";
        }
        if (text.contains("SMS_CODE_INVALID")) {
            return "验证码错误或已过期，请重新获取";
        }
        if (text.contains("BAD_CREDENTIALS")) {
            return "账号或密码不正确";
        }
        if (text.contains("CAPTCHA_REQUIRED")) {
            return "登录失败次数较多，请稍后再试";
        }
        if (text.contains("PLATFORM_ROLE_DENIED")) {
            return "当前账号没有访问权限，请联系管理员开通";
        }
        if (text.contains("PASSWORD_WEAK")) {
            return "密码至少需要 8 位";
        }
        if (text.contains("SMS_PROVIDER") || text.contains("ALIYUN")) {
            return "短信服务暂时不可用，请稍后再试";
        }
        if (message != null && !message.isBlank() && !message.toLowerCase().contains("gateway")) {
            return message;
        }
        return "认证服务请求失败，请稍后再试";
    }

    @Data
    public static class TokenResponse {
        @JsonProperty("access_token")
        private String accessToken;
        @JsonProperty("refresh_token")
        private String refreshToken;
        @JsonProperty("token_type")
        private String tokenType;
        @JsonProperty("expires_in")
        private Long expiresIn;
    }

    @Data
    public static class ResetInitResponse {
        @JsonProperty("masked_phone")
        private String maskedPhone;
        @JsonProperty("ticket_required")
        private boolean ticketRequired;
    }

    @Data
    public static class ResetVerifyResponse {
        @JsonProperty("reset_ticket")
        private String resetTicket;
    }

    @Data
    public static class TokenExchangeResponse {
        @JsonProperty("access_token")
        private String accessToken;
        @JsonProperty("issued_token_type")
        private String issuedTokenType;
        @JsonProperty("token_type")
        private String tokenType;
        @JsonProperty("expires_in")
        private Long expiresIn;
        private String scope;
    }
}
