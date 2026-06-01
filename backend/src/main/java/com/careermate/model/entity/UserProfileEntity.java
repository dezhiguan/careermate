package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@TableName("user_profiles")
public class UserProfileEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String skills;

    private BigDecimal experienceYears;

    private String targetPositions;

    private String targetCompanies;

    private String preferences;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
