package com.careermate.study.dto;

/**
 * 八股题库筛选面上的一个技能标签。
 *
 * @param tag    规范标签
 * @param count  该用户在此标签下的题数（预置标签可能为 0）
 * @param preset 是否预置标签（false 表示用户自建，用于前端区分展示/排序）
 */
public record StudySkillTagVO(
        String tag,
        long count,
        boolean preset
) {
}
