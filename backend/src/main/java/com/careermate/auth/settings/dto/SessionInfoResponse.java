package com.careermate.auth.settings.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class SessionInfoResponse {

    private String sessionId;
    private String deviceName;
    private String ipAddress;
    private Boolean rememberMe;
    private OffsetDateTime lastActive;
    private OffsetDateTime expiresAt;
    private Boolean current;
}
