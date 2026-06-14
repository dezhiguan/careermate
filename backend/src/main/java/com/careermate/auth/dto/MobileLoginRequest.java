package com.careermate.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MobileLoginRequest {

    @NotBlank(message = "phone不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "verifyCode不能为空")
    @Size(min = 4, max = 8, message = "验证码格式不正确")
    private String verifyCode;

    /** From send response; required on login. Alias: outId when provider returns outId. */
    private String challengeId;

    private String outId;

    private String scene = "mobile_login";

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
