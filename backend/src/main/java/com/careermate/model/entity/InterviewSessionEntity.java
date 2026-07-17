package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("interview_sessions")
public class InterviewSessionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long resumeId;

    private Long jobMatchId;

    private String title;

    private String status;

    /** 面试类型：MOCK 模拟 / REAL 真实复盘。 */
    private String sessionType;

    private Integer totalQuestions;

    private Integer answeredQuestions;

    private Integer averageScore;

    private String summary;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
