package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careermate.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("job_matches")
public class JobMatchEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long resumeId;

    private String jobTitle;

    private String companyName;

    private String jdContent;

    private Integer matchScore;

    private String matchLevel;

    @TableField(value = "matched_skills", typeHandler = JsonbStringTypeHandler.class)
    private String matchedSkills;

    @TableField(value = "missing_skills", typeHandler = JsonbStringTypeHandler.class)
    private String missingSkills;

    @TableField(value = "strengths", typeHandler = JsonbStringTypeHandler.class)
    private String strengths;

    @TableField(value = "risks", typeHandler = JsonbStringTypeHandler.class)
    private String risks;

    @TableField(value = "suggestions", typeHandler = JsonbStringTypeHandler.class)
    private String suggestions;

    private String analysisSummary;

    private String status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
