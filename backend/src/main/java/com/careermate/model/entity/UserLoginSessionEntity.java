package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("user_login_sessions")
public class UserLoginSessionEntity {

    @TableId
    private String id;

    private Long userId;

    private String deviceName;

    private String ipAddress;

    private String userAgent;

    private Boolean rememberMe;

    private OffsetDateTime expiresAt;

    private OffsetDateTime lastActive;

    private OffsetDateTime createdAt;
}
