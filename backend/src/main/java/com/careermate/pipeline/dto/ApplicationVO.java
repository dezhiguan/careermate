package com.careermate.pipeline.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单条投递机会（看板卡片）。
 */
@Data
public class ApplicationVO {
    private Long id;
    private Long jdDocId;
    private String company;
    private String roleTitle;
    private String displayName;
    private String stage;
    private String stageLabel;
    /** 派生的真实活动态副标题（改简历中/待面试/待谈薪…）。 */
    private String activity;
    /** 该公司面经（面试练习）数，用于卡片「面经×N」。 */
    private int interviewCount;
    private String resumeVersionId;
    /** 该 JD 已产出的简历版本数（0=还没定制）。 */
    private int resumeVersionCount;
    /** 是否进入谈薪（stage=OFFER 派生）。 */
    private boolean needsSalaryNegotiation;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastActiveAt;
}
