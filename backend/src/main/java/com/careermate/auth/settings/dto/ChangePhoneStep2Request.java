package com.careermate.auth.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ChangePhoneStep2Request {

    @NotBlank(message = "请输入新手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的手机号")
    private String newPhone;

    @NotBlank(message = "请输入新手机号验证码")
    private String verifyCode;

    @NotBlank(message = "challengeId不能为空")
    private String challengeId;

    @NotBlank(message = "oldPhoneTicket不能为空")
    private String oldPhoneTicket;
}
