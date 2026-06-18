package com.careermate.interview.dto;

import com.careermate.common.api.CacheMeta;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class KbQuestionsVO {

    private String query;
    private List<QuestionItem> questions;
    private String aiSummary;
    @JsonProperty("_meta")
    private CacheMeta meta;

    @Data
    public static class QuestionItem {
        private String question;
        private String answer;
        private String category;
    }
}
