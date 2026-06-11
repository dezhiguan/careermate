package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("users")
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String displayName;

    private String avatarUrl;

    private String passwordHash;

    private String email;

    private String role;

    private String status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
