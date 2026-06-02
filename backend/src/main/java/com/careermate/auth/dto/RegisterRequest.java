package com.careermate.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "username不能为空")
    @Size(min = 3, max = 64, message = "username长度必须在3-64之间")
    private String username;

    @NotBlank(message = "password不能为空")
    @Size(min = 8, max = 64, message = "password长度必须在8-64之间")
    private String password;

    @Email(message = "email格式不正确")
    private String email;
}
