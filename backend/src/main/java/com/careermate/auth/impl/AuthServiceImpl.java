package com.careermate.auth.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.audit.AuditActionType;
import com.careermate.audit.AuditService;
import com.careermate.auth.AuthService;
import com.careermate.auth.dto.AuthTokenResponse;
import com.careermate.auth.dto.CurrentUserResponse;
import com.careermate.auth.dto.LoginRequest;
import com.careermate.auth.dto.RegisterRequest;
import com.careermate.auth.dto.UpdateProfileRequest;
import com.careermate.common.api.ErrorCode;
import com.careermate.common.exception.BizException;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.model.entity.UserEntity;
import com.careermate.model.entity.UserProfileEntity;
import com.careermate.security.CurrentUser;
import com.careermate.security.CurrentUserContext;
import com.careermate.security.JwtTokenProvider;
import com.careermate.security.SecurityProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityProperties securityProperties;
    private final AuditService auditService;

    public AuthServiceImpl(
            UserMapper userMapper,
            UserProfileMapper userProfileMapper,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            SecurityProperties securityProperties,
            AuditService auditService
    ) {
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.securityProperties = securityProperties;
        this.auditService = auditService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthTokenResponse register(RegisterRequest request) {
        if (existsByUsername(request.getUsername())) {
            auditService.recordFailure(null, AuditActionType.REGISTER, "USER", null, "username duplicated");
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "用户名已存在");
        }
        if (StringUtils.hasText(request.getEmail()) && existsByEmail(request.getEmail())) {
            auditService.recordFailure(null, AuditActionType.REGISTER, "USER", null, "email duplicated");
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "邮箱已存在");
        }
        UserEntity user = new UserEntity();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmail(StringUtils.hasText(request.getEmail()) ? request.getEmail() : null);
        user.setRole("USER");
        user.setStatus("ACTIVE");
        user.setDisplayName(request.getUsername());
        userMapper.insert(user);

        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(user.getId());
        userProfileMapper.insert(profile);

        auditService.recordSuccess(
                user.getId(),
                AuditActionType.REGISTER,
                "USER",
                String.valueOf(user.getId()),
                "register username=" + user.getUsername()
        );
        return buildTokenResponse(user);
    }

    @Override
    public AuthTokenResponse login(LoginRequest request) {
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, request.getUsername())
                .last("LIMIT 1"));
        if (user == null) {
            auditService.recordFailure(null, AuditActionType.LOGIN, "USER", null, "user not found");
            throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
        }
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            auditService.recordFailure(user.getId(), AuditActionType.LOGIN, "USER", String.valueOf(user.getId()), "user inactive");
            throw new BizException(ErrorCode.FORBIDDEN.getCode(), "用户不可用");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            auditService.recordFailure(user.getId(), AuditActionType.LOGIN, "USER", String.valueOf(user.getId()), "password mismatch");
            throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
        }
        auditService.recordSuccess(
                user.getId(),
                AuditActionType.LOGIN,
                "USER",
                String.valueOf(user.getId()),
                "login username=" + user.getUsername()
        );
        return buildTokenResponse(user);
    }

    @Override
    public CurrentUserResponse currentUser() {
        UserEntity user = requireCurrentUserEntity();
        return toCurrentUserResponse(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CurrentUserResponse updateProfile(UpdateProfileRequest request) {
        UserEntity user = requireCurrentUserEntity();
        user.setDisplayName(request.getDisplayName().trim());
        if (request.getAvatarUrl() != null) {
            String avatarUrl = request.getAvatarUrl().trim();
            if (!avatarUrl.isEmpty() && !isValidAvatarDataUrl(avatarUrl)) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "头像格式不支持");
            }
            user.setAvatarUrl(avatarUrl.isEmpty() ? null : avatarUrl);
        }
        userMapper.updateById(user);
        auditService.recordSuccess(
                user.getId(),
                AuditActionType.PROFILE_UPDATE,
                "USER",
                String.valueOf(user.getId()),
                "update profile displayName=" + user.getDisplayName()
        );
        return toCurrentUserResponse(user);
    }

    private UserEntity requireCurrentUserEntity() {
        CurrentUser currentUser = CurrentUserContext.get();
        if (currentUser == null || !currentUser.isAuthenticated()) {
            throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        }
        UserEntity user = userMapper.selectById(currentUser.getUserId());
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        }
        return user;
    }

    private CurrentUserResponse toCurrentUserResponse(UserEntity user) {
        String displayName = StringUtils.hasText(user.getDisplayName()) ? user.getDisplayName() : user.getUsername();
        return CurrentUserResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(displayName)
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .authenticated(true)
                .build();
    }

    private boolean isValidAvatarDataUrl(String avatarUrl) {
        if (avatarUrl.length() > 600_000) {
            return false;
        }
        String lower = avatarUrl.toLowerCase();
        return lower.startsWith("data:image/jpeg;base64,")
                || lower.startsWith("data:image/jpg;base64,")
                || lower.startsWith("data:image/png;base64,")
                || lower.startsWith("data:image/webp;base64,")
                || lower.startsWith("data:image/gif;base64,");
    }

    private AuthTokenResponse buildTokenResponse(UserEntity user) {
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        return AuthTokenResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(securityProperties.getJwt().getExpirationMs())
                .user(AuthTokenResponse.UserInfo.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .role(user.getRole())
                        .build())
                .build();
    }

    private boolean existsByUsername(String username) {
        return userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, username)) > 0;
    }

    private boolean existsByEmail(String email) {
        return userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getEmail, email)) > 0;
    }
}
