package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("user_password_history")
public class UserPasswordHistoryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String passwordHash;

    private OffsetDateTime createdAt;
}
