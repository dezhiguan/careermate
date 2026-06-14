package com.careermate.auth.controller;

import com.careermate.auth.dto.AuthTokenResponse;
import com.careermate.auth.dto.MobileLoginRequest;
import com.careermate.auth.dto.SmsSendRequest;
import com.careermate.auth.dto.SmsSendResponse;
import com.careermate.auth.sms.MobileAuthService;
import com.careermate.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class MobileAuthController {

    private final MobileAuthService mobileAuthService;

    public MobileAuthController(MobileAuthService mobileAuthService) {
        this.mobileAuthService = mobileAuthService;
    }

    @PostMapping("/sms/send")
    public ApiResponse<SmsSendResponse> sendSmsCode(@RequestBody @Valid SmsSendRequest request) {
        return ApiResponse.success(mobileAuthService.sendCode(request));
    }

    @PostMapping("/mobile/login")
    public ApiResponse<AuthTokenResponse> mobileLogin(@RequestBody @Valid MobileLoginRequest request) {
        return ApiResponse.success(mobileAuthService.login(request));
    }
}
