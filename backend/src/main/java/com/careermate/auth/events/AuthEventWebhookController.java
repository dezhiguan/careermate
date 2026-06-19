package com.careermate.auth.events;

import com.careermate.common.api.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/events", "/api/events"})
public class AuthEventWebhookController {

    private final AuthEventService authEventService;

    public AuthEventWebhookController(AuthEventService authEventService) {
        this.authEventService = authEventService;
    }

    @PostMapping("/session-revoked")
    public ResponseEntity<ApiResponse<AuthEventResult>> sessionRevoked(
            @RequestBody String rawBody,
            @RequestHeader HttpHeaders headers
    ) {
        return handle("session.revoked", rawBody, headers);
    }

    @PostMapping("/password-changed")
    public ResponseEntity<ApiResponse<AuthEventResult>> passwordChanged(
            @RequestBody String rawBody,
            @RequestHeader HttpHeaders headers
    ) {
        return handle("user.password.changed", rawBody, headers);
    }

    private ResponseEntity<ApiResponse<AuthEventResult>> handle(String expectedType, String rawBody, HttpHeaders headers) {
        try {
            return toResponse(authEventService.handle(expectedType, rawBody, headers));
        } catch (IllegalArgumentException ex) {
            return toResponse(AuthEventResult.badRequest("invalid event payload"));
        }
    }

    private ResponseEntity<ApiResponse<AuthEventResult>> toResponse(AuthEventResult result) {
        if (result.status() == 200) {
            return ResponseEntity.ok(ApiResponse.success(result));
        }
        return ResponseEntity.status(result.status()).body(ApiResponse.fail(result.status(), result.message()));
    }
}
