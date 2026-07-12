package com.careermate.auth.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUsernameRequest {

    @NotBlank(message = "请输入账号名")
    @Size(min = 2, max = 32, message = "账号名为2-32位")
    @Pattern(regexp = "^[a-zA-Z0-9_\\u4e00-\\u9fa5]+$", message = "账号名只能包含中文、字母、数字或下划线")
    private String username;

    // 改账号名不再需要短信二次验证：以下两字段仅为兼容旧前端请求体而保留，
    // 去掉 @NotBlank 强校验，服务层也不再消费（否则会在 DTO 校验层就 400 挡住无验证码的改名请求）。
    private String verifyCode;

    private String challengeId;
}
