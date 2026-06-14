package com.careermate.auth;

import com.careermate.auth.dto.LoginRequest;
import com.careermate.auth.service.impl.AuthServiceImpl;
import com.careermate.audit.service.AuditService;
import com.careermate.common.api.ErrorCode;
import com.careermate.common.exception.BizException;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.model.entity.UserEntity;
import com.careermate.security.JwtTokenProvider;
import com.careermate.security.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MobileUserWebLoginTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserProfileMapper userProfileMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private SecurityProperties securityProperties;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        when(userMapper.selectOne(any())).thenAnswer(invocation -> {
            UserEntity user = new UserEntity();
            user.setId(1L);
            user.setUsername("cm_8000_abcd");
            user.setPasswordHash(null);
            user.setRole("USER");
            user.setStatus("ACTIVE");
            return user;
        });
    }

    @Test
    void mobileUserWithoutPasswordCannotWebLogin() {
        LoginRequest request = new LoginRequest();
        request.setUsername("cm_8000_abcd");
        request.setPassword("AnyPassword1!");

        BizException ex = assertThrows(BizException.class, () -> authService.login(request));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
        assertEquals("用户名或密码错误", ex.getMessage());
    }
}
