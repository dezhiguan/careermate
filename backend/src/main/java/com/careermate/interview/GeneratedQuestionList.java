package com.careermate.interview;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeneratedQuestionList(List<LlmQuestion> questions) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LlmQuestion(
        int questionNo,
        String questionType,
        String questionText,
        List<String> referencePoints
    ) {}
}
