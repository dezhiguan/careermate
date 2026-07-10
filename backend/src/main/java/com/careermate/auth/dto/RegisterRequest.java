package com.careermate.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "账号名不能为空")
    @Size(min = 4, max = 32, message = "账号名为4-32位，支持字母、数字、下划线、中划线，且不能全为数字")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码至少8位，且包含字母和数字")
    private String password;

    @Email(message = "请输入有效的邮箱地址")
    private String email;
}
