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
public class InterviewQuestionResponse {

    private Long id;
    private Integer questionNo;
    private String questionType;
    private String questionText;
    private List<String> referencePoints;
    private String answerText;
    private Integer score;
    private String feedback;
    private List<String> strengths;
    private List<String> improvements;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
