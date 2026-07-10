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

    @NotBlank(message = "请输入验证码")
    private String verifyCode;

    @NotBlank(message = "challengeId不能为空")
    private String challengeId;
}
