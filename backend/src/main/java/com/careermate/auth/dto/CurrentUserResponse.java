package com.careermate.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUserResponse {
    private Long userId;
    private String username;
    private String displayName;
    private String avatarUrl;
    private String role;
    private boolean authenticated;
    private String phone;
    private String email;
    private Boolean emailVerified;
    private Boolean hasPassword;
    private String status;
}
