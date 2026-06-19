package com.careermate.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Component
public class ClientAssertionFactory {

    public static final String ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    public ClientAssertionFactory(SecurityProperties securityProperties, ObjectMapper objectMapper) {
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
    }

    public String create() {
        SecurityProperties.AuthGateway authGateway = securityProperties.getAuthGateway();
        try {
            long now = Instant.now().getEpochSecond();
            String header = base64Url(objectMapper.writeValueAsBytes(Map.of(
                    "alg", "RS256",
                    "typ", "JWT",
                    "kid", authGateway.getClientAssertionKid()
            )));
            String payload = base64Url(objectMapper.writeValueAsBytes(Map.of(
                    "iss", authGateway.getClientId(),
                    "sub", authGateway.getClientId(),
                    "aud", authGateway.getTokenEndpointAudience(),
                    "jti", "ca_" + UUID.randomUUID(),
                    "iat", now,
                    "exp", now + 600
            )));
            String signingInput = header + "." + payload;
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(readPrivateKey(Path.of(authGateway.getClientAssertionPrivateKey())));
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + base64Url(signature.sign());
        } catch (Exception ex) {
            throw new IllegalStateException("failed to create client_assertion", ex);
        }
    }

    private RSAPrivateKey readPrivateKey(Path path) throws Exception {
        String pem = Files.readString(path, StandardCharsets.US_ASCII)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(pem);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
