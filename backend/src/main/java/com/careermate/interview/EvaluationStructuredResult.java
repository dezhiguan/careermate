package com.careermate.interview;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EvaluationStructuredResult(
    // 必须是包装类型：用基本类型 int 时，LLM 返回的 JSON 若缺 score 字段，
    // Jackson 会静默填 0 并通过 0~100 的区间校验，用户直接拿到 0 分。
    Integer score,
    String feedback,
    List<String> strengths,
    List<String> improvements
) {}
