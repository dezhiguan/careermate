package com.careermate.auth.settings.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BindEmailRequest {

    @NotBlank(message = "请输入邮箱地址")
    @Email(message = "请输入有效的邮箱地址")
    private String email;
}
