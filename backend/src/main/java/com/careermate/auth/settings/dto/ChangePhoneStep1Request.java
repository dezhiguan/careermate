package com.careermate.auth.settings.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangePhoneStep1Request {

    @NotBlank(message = "请输入验证码")
    private String verifyCode;

    @NotBlank(message = "challengeId不能为空")
    private String challengeId;
}
