package com.careermate.auth;

import com.careermate.auth.dto.AuthTokenResponse;
import com.careermate.auth.dto.CurrentUserResponse;
import com.careermate.auth.dto.LoginRequest;
import com.careermate.auth.dto.RegisterRequest;
import com.careermate.auth.dto.UpdateProfileRequest;

public interface AuthService {

    AuthTokenResponse register(RegisterRequest request);

    AuthTokenResponse login(LoginRequest request);

    CurrentUserResponse currentUser();

    CurrentUserResponse updateProfile(UpdateProfileRequest request);
}
