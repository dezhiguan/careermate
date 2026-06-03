package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careermate.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("interview_questions")
public class InterviewQuestionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long userId;

    private Integer questionNo;

    private String questionType;

    private String questionText;

    @TableField(value = "reference_points", typeHandler = JsonbStringTypeHandler.class)
    private String referencePoints;

    private String answerText;

    private Integer score;

    private String feedback;

    @TableField(value = "strengths", typeHandler = JsonbStringTypeHandler.class)
    private String strengths;

    @TableField(value = "improvements", typeHandler = JsonbStringTypeHandler.class)
    private String improvements;

    private String status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
