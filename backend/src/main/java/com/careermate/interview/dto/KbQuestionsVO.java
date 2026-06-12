package com.careermate.interview.dto;

import java.util.List;
import lombok.Data;

@Data
public class KbQuestionsVO {

    private String query;
    private List<QuestionItem> questions;
    private String aiSummary;

    @Data
    public static class QuestionItem {
        private String question;
        private String answer;
        private String category;
    }
}
