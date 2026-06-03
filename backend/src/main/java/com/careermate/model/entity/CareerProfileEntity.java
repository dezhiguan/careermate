package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careermate.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("career_profiles")
public class CareerProfileEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String targetRole;

    private String targetCity;

    private String seniority;

    private String workMode;

    @TableField(value = "skill_keywords", typeHandler = JsonbStringTypeHandler.class)
    private String skillKeywords;

    private String preferenceSummary;

    private String source;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
