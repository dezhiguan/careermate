package com.careermate.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSessionListItemResponse {

    private Long id;
    private String title;
    private String status;
    /** MOCK 模拟 / REAL 真实复盘。 */
    private String sessionType;
    /** 本场最弱题型。 */
    private String weakness;
    private Integer totalQuestions;
    private Integer answeredQuestions;
    private Integer averageScore;
    private String summary;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
