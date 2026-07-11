package com.careermate.auth.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUsernameRequest {

    @NotBlank(message = "请输入账号名")
    @Size(min = 4, max = 32, message = "账号名为4-32位")
    @Pattern(regexp = "^(?!\\d+$)[a-zA-Z0-9_\\-]+$", message = "账号名只能包含字母、数字、下划线、中划线，且不能全为数字")
    private String username;

    // 改账号名不再需要短信二次验证：以下两字段仅为兼容旧前端请求体而保留，
    // 去掉 @NotBlank 强校验，服务层也不再消费（否则会在 DTO 校验层就 400 挡住无验证码的改名请求）。
    private String verifyCode;

    private String challengeId;
}
