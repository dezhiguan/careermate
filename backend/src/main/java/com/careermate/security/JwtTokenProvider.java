package com.careermate.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JwtTokenProvider {

    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final Map<String, RSAPublicKey> keyCache = new ConcurrentHashMap<>();

    public JwtTokenProvider(SecurityProperties securityProperties, ObjectMapper objectMapper) {
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(securityProperties.getAuthGateway().getTimeoutMs());
        factory.setReadTimeout(securityProperties.getAuthGateway().getTimeoutMs());
        this.restTemplate = new RestTemplate(factory);
    }

    public Claims parseToken(String token) {
        String kid = resolveKid(token);
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(resolvePublicKey(kid))
                .build()
                .parseClaimsJws(token)
                .getBody();
        validateClaims(claims);
        return claims;
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        Object userId = claims.get("user_id");
        if (userId == null) {
            userId = claims.get("userId");
        }
        if (userId instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(userId));
    }

    public String getPlatformRole(String token) {
        Object role = parseToken(token).get("platform_role");
        return role == null ? "USER" : String.valueOf(role);
    }

    private void validateClaims(Claims claims) {
        SecurityProperties.AuthGateway authGateway = securityProperties.getAuthGateway();
        if (!authGateway.getIssuer().equals(claims.getIssuer())) {
            throw new IllegalArgumentException("JWT issuer mismatch");
        }
        Date expiration = claims.getExpiration();
        if (expiration == null || expiration.before(Date.from(Instant.now()))) {
            throw new IllegalArgumentException("JWT expired");
        }
        Object audience = claims.get("aud");
        if (!audienceMatches(audience, authGateway.getAudience())) {
            throw new IllegalArgumentException("JWT audience mismatch");
        }
    }

    private boolean audienceMatches(Object audience, String expected) {
        if (audience instanceof String text) {
            return expected.equals(text);
        }
        if (audience instanceof List<?> values) {
            return values.stream().anyMatch(value -> expected.equals(String.valueOf(value)));
        }
        return false;
    }

    private String resolveKid(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                throw new IllegalArgumentException("invalid JWT");
            }
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String kid = objectMapper.readTree(headerJson).path("kid").asText();
            if (!StringUtils.hasText(kid)) {
                throw new IllegalArgumentException("JWT kid missing");
            }
            return kid;
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid JWT header", ex);
        }
    }

    private RSAPublicKey resolvePublicKey(String kid) {
        return keyCache.computeIfAbsent(kid, this::loadPublicKey);
    }

    private RSAPublicKey loadPublicKey(String kid) {
        try {
            String jwksUrl = securityProperties.getAuthGateway().getBaseUrl() + "/.well-known/jwks.json";
            String body = restTemplate.getForObject(jwksUrl, String.class);
            JsonNode keys = objectMapper.readTree(body).path("keys");
            if (!keys.isArray()) {
                throw new IllegalStateException("JWKS keys missing");
            }
            for (JsonNode key : keys) {
                if (kid.equals(key.path("kid").asText())) {
                    BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(key.path("n").asText()));
                    BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(key.path("e").asText()));
                    return (RSAPublicKey) KeyFactory.getInstance("RSA")
                            .generatePublic(new RSAPublicKeySpec(modulus, exponent));
                }
            }
            throw new IllegalStateException("JWKS kid not found: " + kid);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to load JWKS public key", ex);
        }
    }
}
