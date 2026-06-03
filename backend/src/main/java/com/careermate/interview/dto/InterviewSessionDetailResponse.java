package com.careermate.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSessionDetailResponse {

    private Long id;
    private Long resumeId;
    private Long jobMatchId;
    private String title;
    private String status;
    private Integer totalQuestions;
    private Integer answeredQuestions;
    private Integer averageScore;
    private String summary;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<InterviewQuestionResponse> questions;
}
