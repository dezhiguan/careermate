package com.careermate.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
public class LoginRequest {

    private String username;

    private String account;

    @NotBlank(message = "password不能为空")
    private String password;

    private Boolean rememberMe;

    private String captcha;

    private String captchaChallengeId;

    public String resolveAccount() {
        if (StringUtils.hasText(account)) {
            return account.trim();
        }
        return username == null ? null : username.trim();
    }

    public boolean isRememberMe() {
        return Boolean.TRUE.equals(rememberMe);
    }
}
