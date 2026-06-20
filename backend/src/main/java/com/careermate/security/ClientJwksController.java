package com.careermate.security;

import com.careermate.common.api.ErrorCode;
import com.careermate.common.exception.BizException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClientJwksController {

    private final SecurityProperties securityProperties;

    public ClientJwksController(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @GetMapping(value = "/api/.well-known/careermate-backend-jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> jwks() {
        Path jwksPath = resolveJwksPath();
        if (!Files.isRegularFile(jwksPath)) {
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "客户端公钥未配置");
        }
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Files.readString(jwksPath, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "客户端公钥读取失败");
        }
    }

    private Path resolveJwksPath() {
        String privateKey = securityProperties.getAuthGateway().getClientAssertionPrivateKey();
        if (!StringUtils.hasText(privateKey)) {
            return Path.of("config/keys/careermate-backend.jwks.json");
        }
        String normalized = privateKey.endsWith(".pem")
                ? privateKey.substring(0, privateKey.length() - ".pem".length()) + ".jwks.json"
                : privateKey + ".jwks.json";
        return Path.of(normalized).normalize();
    }
}
