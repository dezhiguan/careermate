package com.careermate.study.dto;

import java.time.LocalDateTime;

/**
 * 八股题库一条：题 + 手写答案 + 技能标签。
 */
public record StudyNoteVO(
        Long id,
        String question,
        String skillTag,
        String answer,
        LocalDateTime updatedAt
) {
}
