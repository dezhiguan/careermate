package com.careermate.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MobileLoginRequest {

    @NotBlank(message = "请输入手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的手机号")
    private String phone;

    @NotBlank(message = "请输入验证码")
    @Size(min = 4, max = 8, message = "请输入4-8位验证码")
    private String verifyCode;

    /** From send response; required on login. Alias: outId when provider returns outId. */
    private String challengeId;

    private String outId;

    private String scene = "mobile_login";

    private Boolean rememberMe;

    public boolean isRememberMe() {
        return Boolean.TRUE.equals(rememberMe);
    }

    public String resolveChallengeId() {
        if (org.springframework.util.StringUtils.hasText(challengeId)) {
            return challengeId.trim();
        }
        if (org.springframework.util.StringUtils.hasText(outId)) {
            return outId.trim();
        }
        return null;
    }
}
