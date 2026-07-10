package com.careermate.auth.settings.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CancelAccountRequest {

    @NotBlank(message = "请输入验证码")
    private String verifyCode;

    @NotBlank(message = "challengeId不能为空")
    private String challengeId;

    @NotBlank(message = "请输入「注销」以确认操作")
    private String confirmText;
}
