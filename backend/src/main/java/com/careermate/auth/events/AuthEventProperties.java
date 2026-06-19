package com.careermate.auth.events;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "careermate.security.auth-events")
public class AuthEventProperties {

    private String hmacSecret = "";
    private String signatureHeader = "X-Auth-Event-Signature";
    private String timestampHeader = "X-Auth-Event-Timestamp";
    private Duration idempotencyTtl = Duration.ofDays(7);
    private Duration revokedJtiTtl = Duration.ofDays(7);
    private Duration userRevocationTtl = Duration.ofDays(30);
}
