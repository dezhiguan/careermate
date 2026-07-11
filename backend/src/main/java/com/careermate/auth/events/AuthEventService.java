package com.careermate.auth.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
public class AuthEventService {

    public static final String REVOKED_JTI_PREFIX = "careermate:auth:revoked:jti:";
    public static final String EVENT_ID_PREFIX = "careermate:auth:event:";
    public static final String USER_REVOKED_AFTER_PREFIX = "careermate:auth:revoked:user:";

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final AuthEventProperties properties;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final LocalAccountPurger localAccountPurger;

    public AuthEventService(
            AuthEventProperties properties,
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            LocalAccountPurger localAccountPurger
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.localAccountPurger = localAccountPurger;
    }

    public AuthEventResult handle(String expectedType, String rawBody, HttpHeaders headers) {
        if (redisTemplate == null) {
            return AuthEventResult.unavailable("auth event storage unavailable");
        }
        if (!verifySignature(rawBody, headers)) {
            return AuthEventResult.invalidSignature();
        }

        AuthEventPayload payload = parse(rawBody);
        if (!StringUtils.hasText(payload.eventId())) {
            return AuthEventResult.badRequest("event_id required");
        }
        String effectiveType = StringUtils.hasText(payload.type()) ? payload.type() : expectedType;

        String eventKey = EVENT_ID_PREFIX + payload.eventId();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(eventKey))) {
            return AuthEventResult.duplicate(payload.eventId());
        }

        int revokedCount = revokeTokens(payload);
        if ("user.password.changed".equals(effectiveType) || StringUtils.hasText(userKey(payload))) {
            revokeUser(payload);
        }
        // 应用级注销到期：网关发 user.app_removed(app=careermate) → 清理本地个人数据（PIPL 合规）。
        if ("user.app_removed".equals(effectiveType) && isCareermateApp(payload) && StringUtils.hasText(payload.userId())) {
            try {
                localAccountPurger.purgeByAuthUserId(Long.parseLong(payload.userId()));
            } catch (RuntimeException ex) {
                log.warn("local account purge failed for authUserId={}: {}", payload.userId(), ex.getMessage());
            }
        }
        redisTemplate.opsForValue().set(eventKey, effectiveType, properties.getIdempotencyTtl());

        return AuthEventResult.accepted(payload.eventId(), revokedCount);
    }

    public boolean isJwtRevoked(AuthJwtToken token) {
        if (redisTemplate == null) {
            return false;
        }
        try {
            if (StringUtils.hasText(token.jti())) {
                Boolean jtiRevoked = redisTemplate.hasKey(REVOKED_JTI_PREFIX + token.jti());
                if (Boolean.TRUE.equals(jtiRevoked)) {
                    return true;
                }
            }
            if (StringUtils.hasText(token.userKey()) && token.issuedAtEpochSeconds() != null) {
                String revokedAfter = redisTemplate.opsForValue().get(USER_REVOKED_AFTER_PREFIX + token.userKey());
                if (StringUtils.hasText(revokedAfter)) {
                    return token.issuedAtEpochSeconds() <= Long.parseLong(revokedAfter);
                }
            }
        } catch (RuntimeException ex) {
            log.warn("auth event revocation storage unavailable; allowing JWT validation to continue");
            return false;
        }
        return false;
    }

    // ─── 本地主动吊销（供 CareerMate 自身的退出/踢设备/改密/换绑调用）───────────────
    // 复用与 auth-gateway webhook 相同的 Redis 存储与过滤器判定逻辑：
    // - 单 token 吊销：写 revoked:jti:<jti>
    // - 用户级吊销：写 revoked:user:<userKey> = cutoff，凡 iat<=cutoff 的 token 均失效

    /**
     * 吊销单个 access token（按 jti）。用于单设备退出、踢出指定会话。
     *
     * @param jti                token 的 jti
     * @param tokenExpEpochSeconds token 的过期时间（epoch 秒），用于设置刚好覆盖到过期的 TTL；为 null 则用默认 TTL
     */
    public void revokeJti(String jti, Long tokenExpEpochSeconds) {
        if (redisTemplate == null || !StringUtils.hasText(jti)) {
            return;
        }
        Duration ttl = properties.getRevokedJtiTtl();
        if (tokenExpEpochSeconds != null) {
            long remaining = tokenExpEpochSeconds - Instant.now().getEpochSecond();
            ttl = remaining > 0 ? Duration.ofSeconds(remaining) : Duration.ofSeconds(1);
        }
        try {
            redisTemplate.opsForValue().set(REVOKED_JTI_PREFIX + jti, "local", ttl);
        } catch (RuntimeException ex) {
            log.warn("revokeJti failed (storage unavailable); jti stays valid until natural expiry");
        }
    }

    /**
     * 用户级吊销：将该用户在 cutoff（epoch 秒）之前签发的所有 access token 全部失效。
     * 用于"退出全部设备"、改密/换绑后踢下全部设备。
     */
    public void revokeUserAfter(String userKey, long cutoffEpochSeconds) {
        if (redisTemplate == null || !StringUtils.hasText(userKey)) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    USER_REVOKED_AFTER_PREFIX + userKey,
                    String.valueOf(cutoffEpochSeconds),
                    properties.getUserRevocationTtl()
            );
        } catch (RuntimeException ex) {
            log.warn("revokeUserAfter failed (storage unavailable); tokens stay valid until natural expiry");
        }
    }

    private AuthEventPayload parse(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, AuthEventPayload.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid event payload", ex);
        }
    }

    private int revokeTokens(AuthEventPayload payload) {
        Set<String> jtis = jtis(payload);
        for (String jti : jtis) {
            redisTemplate.opsForValue().set(REVOKED_JTI_PREFIX + jti, payload.eventId(), ttlFor(payload));
        }
        return jtis.size();
    }

    private Set<String> jtis(AuthEventPayload payload) {
        Set<String> result = new LinkedHashSet<>();
        if (StringUtils.hasText(payload.jti())) {
            result.add(payload.jti());
        }
        if (payload.jtis() != null) {
            for (String jti : payload.jtis()) {
                if (StringUtils.hasText(jti)) {
                    result.add(jti);
                }
            }
        }
        Object dataJti = payload.data() == null ? null : payload.data().get("jti");
        if (dataJti != null && StringUtils.hasText(String.valueOf(dataJti))) {
            result.add(String.valueOf(dataJti));
        }
        Object dataJtis = payload.data() == null ? null : payload.data().get("jtis");
        if (dataJtis instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    result.add(String.valueOf(item));
                }
            }
        }
        return result;
    }

    private void revokeUser(AuthEventPayload payload) {
        String userKey = userKey(payload);
        if (!StringUtils.hasText(userKey)) {
            return;
        }
        long occurredAt = occurredAtEpochSeconds(payload);
        redisTemplate.opsForValue().set(
                USER_REVOKED_AFTER_PREFIX + userKey,
                String.valueOf(occurredAt),
                properties.getUserRevocationTtl()
        );
    }

    private long occurredAtEpochSeconds(AuthEventPayload payload) {
        Object value = payload.occurredAt();
        if (value == null) {
            return Instant.now().getEpochSecond();
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            // Try ISO-8601 below.
        }
        try {
            return Instant.parse(text).getEpochSecond();
        } catch (RuntimeException ignored) {
            return Instant.now().getEpochSecond();
        }
    }

    /** user.app_removed 事件的 app 是否为 careermate（从 payload.data.app 读取）。 */
    private boolean isCareermateApp(AuthEventPayload payload) {
        Object app = payload.data() == null ? null : payload.data().get("app");
        return app != null && "careermate".equalsIgnoreCase(String.valueOf(app));
    }

    private String userKey(AuthEventPayload payload) {
        if (StringUtils.hasText(payload.userId())) {
            return payload.userId();
        }
        if (StringUtils.hasText(payload.sub())) {
            return payload.sub();
        }
        Object dataUserId = payload.data() == null ? null : payload.data().get("user_id");
        return dataUserId == null ? null : String.valueOf(dataUserId);
    }

    private Duration ttlFor(AuthEventPayload payload) {
        if (payload.exp() == null) {
            return properties.getRevokedJtiTtl();
        }
        long seconds = payload.exp() - Instant.now().getEpochSecond();
        return seconds > 0 ? Duration.ofSeconds(seconds) : Duration.ofSeconds(1);
    }

    private boolean verifySignature(String rawBody, HttpHeaders headers) {
        String provided = signature(headers);
        if (!StringUtils.hasText(provided) || !StringUtils.hasText(properties.getHmacSecret())) {
            return false;
        }
        String expected = hmac(rawBody);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                normalizeSignature(provided).getBytes(StandardCharsets.US_ASCII)
        );
    }

    private String signature(HttpHeaders headers) {
        String signature = headers.getFirst(properties.getSignatureHeader());
        if (!StringUtils.hasText(signature)) {
            signature = headers.getFirst("X-Hub-Signature-256");
        }
        if (!StringUtils.hasText(signature)) {
            signature = headers.getFirst("X-Signature");
        }
        return signature;
    }

    private String normalizeSignature(String signature) {
        String value = signature.trim();
        int equals = value.indexOf('=');
        if (equals >= 0) {
            value = value.substring(equals + 1);
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private String hmac(String rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.getHmacSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("failed to calculate auth event HMAC", ex);
        }
    }
}
