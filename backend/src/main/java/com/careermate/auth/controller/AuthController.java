package com.careermate.auth.controller;

import com.careermate.auth.dto.AuthTokenResponse;
import com.careermate.auth.dto.CurrentUserResponse;
import com.careermate.auth.dto.LoginRequest;
import com.careermate.auth.dto.RegisterRequest;
import com.careermate.auth.dto.UpdateProfileRequest;
import com.careermate.auth.gateway.AuthGatewayClient;
import com.careermate.auth.gateway.AuthGatewayCookieSupport;
import com.careermate.auth.service.AuthService;
import com.careermate.common.api.ApiResponse;
import com.careermate.common.api.ErrorCode;
import com.careermate.common.exception.BizException;
import com.careermate.security.SecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthGatewayClient authGatewayClient;
    private final AuthGatewayCookieSupport cookieSupport;
    private final SecurityProperties.AuthGateway gatewayProps;

    public AuthController(AuthService authService, AuthGatewayClient authGatewayClient,
                          AuthGatewayCookieSupport cookieSupport, SecurityProperties securityProperties) {
        this.authService = authService;
        this.authGatewayClient = authGatewayClient;
        this.cookieSupport = cookieSupport;
        this.gatewayProps = securityProperties.getAuthGateway();
    }

    /**
     * 静默续期：读 refresh cookie → 网关换发新 access token（并轮换 refresh）→ 写新 cookie，返回新 AT。
     * 前端在 access token 过期(401)时自动调用，避免闲置/合盖超过 15 分钟被直接踢出登录。
     * 匿名端点（凭 refresh cookie 鉴权，不需 Bearer）。
     */
    @PostMapping("/refresh")
    public ApiResponse<AuthTokenResponse> refresh(HttpServletRequest request) {
        String refreshToken = readRefreshCookie(request);
        if (!StringUtils.hasText(refreshToken)) {
            throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), "登录状态已失效，请重新登录");
        }
        AuthGatewayClient.TokenResponse tr;
        try {
            tr = authGatewayClient.refresh(refreshToken);
        } catch (RuntimeException ex) {
            throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), "登录已过期，请重新登录");
        }
        // 轮换后的新 refresh token 写回 cookie（沿用长有效期，真正的过期以网关 token TTL 为准）
        cookieSupport.writeRefreshCookie(tr.getRefreshToken(), true);
        return ApiResponse.success(AuthTokenResponse.builder()
                .token(tr.getAccessToken())
                .tokenType(StringUtils.hasText(tr.getTokenType()) ? tr.getTokenType() : "Bearer")
                .expiresIn(tr.getExpiresIn())
                .build());
    }

    private String readRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie c : request.getCookies()) {
            if (gatewayProps.getRefreshCookieName().equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    /** 获取/刷新图形验证码（连续失败后前端点"看不清换一张"）。代理到 auth-gateway，返回 {captchaImage, challengeId}。 */
    @GetMapping("/captcha")
    public ApiResponse<Map<String, Object>> captcha() {
        return ApiResponse.success(authGatewayClient.getCaptcha());
    }

    @PostMapping("/register")
    public ApiResponse<AuthTokenResponse> register(@RequestBody @Valid RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@RequestBody @Valid LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me() {
        return ApiResponse.success(authService.currentUser());
    }

    @PutMapping("/me")
    public ApiResponse<CurrentUserResponse> updateProfile(@RequestBody @Valid UpdateProfileRequest request) {
        return ApiResponse.success(authService.updateProfile(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.success(null);
    }

    @PostMapping("/logout-all")
    public ApiResponse<Void> logoutAll() {
        authService.logoutAll();
        return ApiResponse.success(null);
    }
}
