package com.careermate.auth.controller;

import com.careermate.auth.dto.PasswordResetConfirmRequest;
import com.careermate.auth.dto.PasswordResetConfirmResponse;
import com.careermate.auth.dto.PasswordResetSmsSendRequest;
import com.careermate.auth.dto.SmsSendResponse;
import com.careermate.auth.sms.PasswordResetService;
import com.careermate.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/password-reset")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/sms/send")
    public ApiResponse<SmsSendResponse> sendSms(@RequestBody @Valid PasswordResetSmsSendRequest request) {
        return ApiResponse.success(passwordResetService.sendSms(request));
    }

    @PostMapping("/confirm")
    public ApiResponse<PasswordResetConfirmResponse> confirm(@RequestBody @Valid PasswordResetConfirmRequest request) {
        return ApiResponse.success(passwordResetService.confirm(request));
    }
}
