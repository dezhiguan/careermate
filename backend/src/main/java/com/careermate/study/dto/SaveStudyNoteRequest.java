package com.careermate.study.dto;

import lombok.Data;

/**
 * 收录/更新一条八股题（题 + 手写答案）。按 question 去重 upsert。
 */
@Data
public class SaveStudyNoteRequest {
    private String question;
    private String skillTag;
    private String answer;
}
