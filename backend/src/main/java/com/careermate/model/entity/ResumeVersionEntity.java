package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careermate.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName(value = "resume_versions", autoResultMap = true)
public class ResumeVersionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String versionId;
    private Long userId;
    private Long tenantId;
    private String sessionId;
    private Long sourceResumeId;
    private String parentVersionId;
    private String targetJdId;
    private String targetJdLabel;
    private String versionName;
    private String contentMarkdown;
    @TableField(value = "optimization_notes", typeHandler = JsonbStringTypeHandler.class)
    private String optimizationNotes;
    private BigDecimal aiScore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
