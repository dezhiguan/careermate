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
import com.careermate.config.PooledHttpClientFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@lombok.extern.slf4j.Slf4j
public class AuthGatewayClient {

    private static final String TOKEN_EXCHANGE_GRANT = "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";

    /**
     * 「记住我」表单参数名，必须与网关 {@code AuthLoginController} 的
     * {@code @RequestParam(name = "remember")} 一致。
     *
     * <p>此前两条登录路径都发的是 {@code remember_me}，网关按未传处理，refresh token 一律按默认
     * TTL 签发——「30 天内免登录」勾了也没用，且因为不报错而长期无人察觉。
     */
    private static final String REMEMBER_PARAM = "remember";

    private final SecurityProperties.AuthGateway properties;
    private final ClientAssertionFactory clientAssertionFactory;
    private final RestTemplate restTemplate;
    private final RestTemplate smsRestTemplate;
    private final ObjectMapper objectMapper;

    public AuthGatewayClient(SecurityProperties securityProperties, ClientAssertionFactory clientAssertionFactory, ObjectMapper objectMapper) {
        this.properties = securityProperties.getAuthGateway();
        this.clientAssertionFactory = clientAssertionFactory;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate(
                PooledHttpClientFactory.create(properties.getTimeoutMs()));
        // 短信链路多一跳服务商，单独给更长的超时；其余认证调用仍快速失败
        this.smsRestTemplate = new RestTemplate(
                PooledHttpClientFactory.create(properties.getSmsTimeoutMs()));
    }

    public TokenResponse loginPassword(String account, String password) {
        return loginPassword(account, password, false);
    }

    public TokenResponse loginPassword(String account, String password, boolean rememberMe) {
        return loginPassword(account, password, rememberMe, null, null);
    }

    /**
     * 密码登录。captcha/challengeId 透传给 auth-gateway：失败次数达阈值后网关会要求图形验证码，
     * 校验通过才放行。网关返回 CAPTCHA_REQUIRED 时，{@link #exchange} 会抛出 CaptchaRequiredException 携带图片。
     */
    public TokenResponse loginPassword(String account, String password, boolean rememberMe,
                                       String captcha, String challengeId) {
        MultiValueMap<String, String> form = clientForm();
        form.add("account", account);
        form.add("password", password);
        form.add("target_aud", properties.getAudience());
        if (rememberMe) {
            form.add(REMEMBER_PARAM, "true");
        }
        if (captcha != null && !captcha.isBlank()) {
            form.add("captcha", captcha);
        }
        if (challengeId != null && !challengeId.isBlank()) {
            form.add("challenge_id", challengeId);
        }
        return postForm("/auth/login/password", form, TokenResponse.class);
    }

    /**
     * 同步账号名到 auth-gateway（凭证/身份统一存网关，登录也按网关这份解析账号）。
     * 用当前用户 access token 鉴权。网关无需旧密码。注意：网关用户名规则不含中划线。
     */
    public void setCredentialUsername(String userAccessToken, String username) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("username", username);
        credentialPost("/auth/credential/set-username", userAccessToken, body);
    }

    /**
     * 同步邮箱到 auth-gateway（使邮箱可作为登录账号）。用当前用户 access token 鉴权。
     * 网关侧若账号已有密码会要求校验密码；CareerMate 绑邮箱不收密码，故 password 传空，
     * 该情况下网关会拒（由调用方按 best-effort 处理）。
     */
    public void bindCredentialEmail(String userAccessToken, String email, String password) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("email", email);
        body.put("password", password);
        credentialPost("/auth/credential/bind-email", userAccessToken, body);
    }

    private void credentialPost(String path, String userAccessToken, java.util.Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String bearer = userAccessToken != null && userAccessToken.startsWith("Bearer ")
                ? userAccessToken : "Bearer " + userAccessToken;
        headers.set("Authorization", bearer);
        exchange(path, new HttpEntity<>(body, headers), Map.class);
    }

    /** 代理获取一张新的图形验证码（前端"看不清换一张"）。返回 {captchaImage, challengeId}。 */
    public Map<String, Object> getCaptcha() {
        try {
            HttpHeaders headers = new HttpHeaders();
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>)
                    restTemplate.exchange(properties.getBaseUrl() + "/auth/captcha",
                            org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            throw toBizException(ex, "/auth/captcha");
        } catch (Exception ex) {
            // 原来这里把异常整个吞掉，只剩一句「认证服务不可用」，线上偶发 500 时既看不出是
            // 连接超时、读超时还是 TLS 握手失败，也没有 path 可定位——留痕后才谈得上排查。
            log.warn("auth-gateway 调用失败 path=/auth/captcha: {}", ex.toString());
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "认证服务不可用");
        }
    }

    /**
     * 应用级注销：委托网关把当前用户在指定 app 的 membership 置为 PENDING_DELETION（Bearer 识别用户）。
     * 短信+确认字 step-up 由 CareerMate 自己完成，故此处仅传 Bearer。返回网关响应体（含 deletionScheduledAt）。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> requestAppDeletion(String userAccessToken, String app) {
        return (Map<String, Object>) bearerExchange(org.springframework.http.HttpMethod.POST,
                "/auth/apps/" + app + "/deletion-request", userAccessToken);
    }

    /** 撤销应用级注销：委托网关把该 membership 恢复 ACTIVE。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> cancelAppDeletion(String userAccessToken, String app) {
        return (Map<String, Object>) bearerExchange(org.springframework.http.HttpMethod.DELETE,
                "/auth/apps/" + app + "/deletion-request", userAccessToken);
    }

    private Object bearerExchange(org.springframework.http.HttpMethod method, String path, String userAccessToken) {
        HttpHeaders headers = new HttpHeaders();
        String bearer = userAccessToken != null && userAccessToken.startsWith("Bearer ")
                ? userAccessToken : "Bearer " + userAccessToken;
        headers.set("Authorization", bearer);
        try {
            return restTemplate.exchange(properties.getBaseUrl() + path, method,
                    new HttpEntity<>(headers), Map.class).getBody();
        } catch (HttpStatusCodeException ex) {
            throw toBizException(ex, path);
        } catch (Exception ex) {
            // 原来这里把异常整个吞掉，只剩一句「认证服务不可用」，线上偶发 500 时既看不出是
            // 连接超时、读超时还是 TLS 握手失败，也没有 path 可定位——留痕后才谈得上排查。
            log.warn("auth-gateway 调用失败 path={}: {}", path, ex.toString());
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "认证服务不可用");
        }
    }

    public TokenResponse loginMobile(String phone, String code) {
        return loginMobile(phone, code, false);
    }

    public TokenResponse loginMobile(String phone, String code, boolean rememberMe) {
        MultiValueMap<String, String> form = clientForm();
        form.add("phone", phone);
        form.add("code", code);
        form.add("target_aud", properties.getAudience());
        if (rememberMe) {
            form.add(REMEMBER_PARAM, "true");
        }
        return postForm("/auth/login/mobile", form, TokenResponse.class);
    }

    public void revokeSession(String jti) {
        try {
            postJson("/auth/sessions/revoke", Map.of("jti", jti), Map.class);
        } catch (Exception ignored) {
            // best-effort; session_version invalidation is the primary guard
        }
    }

    public void sendSms(String phone, String scene) {
        postJson("/auth/sms/send", Map.of("phone", phone, "scene", scene), Map.class, smsRestTemplate);
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
        return postJson(path, body, responseType, restTemplate);
    }

    private <T> T postJson(String path, Object body, Class<T> responseType, RestTemplate template) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return exchange(path, new HttpEntity<>(body, headers), responseType, template);
    }

    private <T> T exchange(String path, HttpEntity<?> entity, Class<T> responseType) {
        return exchange(path, entity, responseType, restTemplate);
    }

    private <T> T exchange(String path, HttpEntity<?> entity, Class<T> responseType, RestTemplate template) {
        try {
            ResponseEntity<T> response = template.postForEntity(properties.getBaseUrl() + path, entity, responseType);
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            throw toBizException(ex, path);
        } catch (Exception ex) {
            // 原来这里把异常整个吞掉，只剩一句「认证服务不可用」，线上偶发 500 时既看不出是
            // 连接超时、读超时还是 TLS 握手失败，也没有 path 可定位——留痕后才谈得上排查。
            log.warn("auth-gateway 调用失败 path={}: {}", path, ex.toString());
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "认证服务不可用");
        }
    }

    /** 网关响应体截断后入日志，避免异常页把日志刷爆。 */
    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        String flat = body.replaceAll("\\s+", " ").trim();
        return flat.length() <= 500 ? flat : flat.substring(0, 500) + "...(truncated)";
    }

    private BizException toBizException(HttpStatusCodeException ex, String path) {
        // 网关自身 5xx：friendlyGatewayMessage 会把原始信息抹成一句「认证服务请求失败」，
        // 而这里原本一行日志都不打——线上只剩一个没有上下文的 500，等于不可诊断。
        // 4xx 是正常业务结果（密码错、验证码），不记；只记 5xx，且只记响应体不记请求体，避免落密码。
        if (ex.getStatusCode().is5xxServerError()) {
            log.error("auth-gateway 返回 {} path={} body={}", ex.getStatusCode().value(), path,
                    abbreviate(ex.getResponseBodyAsString()));
        }
        String gatewayCode = null;
        String gatewayMessage = null;
        String captchaImage = null;
        String captchaChallengeId = null;
        try {
            Map<String, Object> payload = objectMapper.readValue(ex.getResponseBodyAsString(), new TypeReference<>() {});
            Object code = payload.getOrDefault("error", payload.get("code"));
            Object message = payload.getOrDefault("message", payload.get("msg"));
            gatewayCode = code == null ? null : String.valueOf(code);
            gatewayMessage = message == null ? null : String.valueOf(message);
            Object img = payload.get("captchaImage");
            Object cid = payload.get("challengeId");
            captchaImage = img == null ? null : String.valueOf(img);
            captchaChallengeId = cid == null ? null : String.valueOf(cid);
        } catch (Exception ignored) {
            // keep default mapping below
        }

        // 网关要求图形验证码（HTTP 423 CAPTCHA_REQUIRED）：透传图片与 challengeId 给前端展示回填
        if ((gatewayCode != null && gatewayCode.toUpperCase().contains("CAPTCHA_REQUIRED"))
                || ex.getStatusCode().value() == 423) {
            String msg = gatewayMessage != null && !gatewayMessage.isBlank()
                    ? gatewayMessage : "请输入图形验证码后重试";
            throw new com.careermate.auth.captcha.CaptchaRequiredException(msg, captchaChallengeId, captchaImage);
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
