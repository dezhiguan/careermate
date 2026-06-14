package com.careermate.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordResetConfirmRequest {

    @NotBlank(message = "请输入手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的手机号")
    private String phone;

    @NotBlank(message = "请输入验证码")
    @Size(min = 4, max = 8, message = "请输入4-8位验证码")
    private String verifyCode;

    @NotBlank(message = "请先获取验证码")
    private String challengeId;

    @NotBlank(message = "请输入新密码")
    @Size(min = 8, max = 64, message = "密码长度需在8-64位之间")
    private String newPassword;
}
