package com.careermate.auth.gateway;

import com.careermate.common.api.ErrorCode;
import com.careermate.common.exception.BizException;
import com.careermate.security.ClientAssertionFactory;
import com.careermate.security.SecurityProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    public AuthGatewayClient(SecurityProperties securityProperties, ClientAssertionFactory clientAssertionFactory) {
        this.properties = securityProperties.getAuthGateway();
        this.clientAssertionFactory = clientAssertionFactory;
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
        return postJson("/auth/password/reset/init", Map.of("account", account), ResetInitResponse.class);
    }

    public ResetVerifyResponse resetVerify(String account, String code) {
        return postJson("/auth/password/reset/verify", Map.of("account", account, "code", code), ResetVerifyResponse.class);
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
            throw new BizException(ex.getStatusCode().value(), "认证服务请求失败");
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "认证服务不可用");
        }
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
