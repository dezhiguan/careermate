package com.careermate.auth.settings;

import com.careermate.auth.settings.dto.*;
import com.careermate.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user")
public class UserSettingsController {

    private final UserSettingsService settingsService;

    public UserSettingsController(UserSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    // ─── 邮箱绑定 Phase 1（保存，不验证）──────────────────────────────────────

    @PostMapping("/settings/email/bind")
    public ApiResponse<Void> bindEmail(@RequestBody @Valid BindEmailRequest request) {
        settingsService.bindEmail(request);
        return ApiResponse.success(null);
    }

    // ─── 账号名设置─────────────────────────────────────────────────────────────

    @PutMapping("/settings/username")
    public ApiResponse<Void> updateUsername(@RequestBody @Valid UpdateUsernameRequest request) {
        settingsService.updateUsername(request);
        return ApiResponse.success(null);
    }

    // ─── 密码设置────────────────────────────────────────────────────────────────

    @PostMapping("/settings/password")
    public ApiResponse<Void> setPassword(@RequestBody @Valid SetPasswordRequest request) {
        settingsService.setPassword(request);
        return ApiResponse.success(null);
    }

    // ─── 换绑手机号──────────────────────────────────────────────────────────────

    @PostMapping("/settings/phone/verify-old")
    public ApiResponse<String> verifyOldPhone(@RequestBody @Valid ChangePhoneStep1Request request) {
        String ticket = settingsService.verifyOldPhone(request);
        return ApiResponse.success(ticket);
    }

    @PostMapping("/settings/phone/bind-new")
    public ApiResponse<Void> bindNewPhone(@RequestBody @Valid ChangePhoneStep2Request request) {
        settingsService.bindNewPhone(request);
        return ApiResponse.success(null);
    }

    // ─── 账号注销────────────────────────────────────────────────────────────────

    @PostMapping("/account/cancel")
    public ApiResponse<Void> cancelAccount(@RequestBody @Valid CancelAccountRequest request) {
        settingsService.requestCancellation(request);
        return ApiResponse.success(null);
    }

    @PostMapping("/account/cancel/revoke")
    public ApiResponse<Void> revokeCancellation() {
        settingsService.revokeCancellation();
        return ApiResponse.success(null);
    }

    // ─── 会话管理────────────────────────────────────────────────────────────────

    @GetMapping("/sessions")
    public ApiResponse<List<SessionInfoResponse>> listSessions(
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        return ApiResponse.success(settingsService.listSessions(sessionId));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> revokeSession(@PathVariable String sessionId) {
        settingsService.revokeSession(sessionId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/sessions")
    public ApiResponse<Void> revokeAllOtherSessions(
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        settingsService.revokeAllOtherSessions(sessionId != null ? sessionId : "");
        return ApiResponse.success(null);
    }

    // ─── Onboarding────────────────────────────────────────────────────────────

    @PostMapping("/onboarding/complete")
    public ApiResponse<Void> completeOnboarding() {
        settingsService.completeOnboarding();
        return ApiResponse.success(null);
    }

    // ─── 协议同意──────────────────────────────────────────────────────────────

    @PostMapping("/terms/agree")
    public ApiResponse<Void> agreeTerms(@RequestParam(defaultValue = "v1") String version) {
        settingsService.agreeTerms(version);
        return ApiResponse.success(null);
    }
}
