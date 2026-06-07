package com.careermate.interview;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EvaluationStructuredResult(
    int score,
    String feedback,
    List<String> strengths,
    List<String> improvements
) {}
