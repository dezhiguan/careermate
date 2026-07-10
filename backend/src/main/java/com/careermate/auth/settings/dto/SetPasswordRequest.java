package com.careermate.auth.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SetPasswordRequest {

    @NotBlank(message = "请输入新密码")
    @Size(min = 8, max = 64, message = "密码为8-64位")
    private String newPassword;

    @NotBlank(message = "请输入验证码")
    private String verifyCode;

    @NotBlank(message = "challengeId不能为空")
    private String challengeId;
}
