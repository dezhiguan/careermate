package com.careermate.auth.events;

public record AuthJwtToken(String jti, String userKey, Long issuedAtEpochSeconds) {
}
