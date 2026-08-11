package com.careermate.study.dto;

import java.util.List;

/**
 * 八股题库筛选面数据：预置标签 ∪ 用户自建标签，已按展示顺序排好。
 *
 * @param tags     展示顺序的标签列表（预置在前，用户自建按题数倒序在后）
 * @param tagged   有标签的题数合计
 * @param untagged 未打标签的题数（只在「全部」下可见，供前端提示用户补标签）
 */
public record StudySkillTagsVO(
        List<StudySkillTagVO> tags,
        long tagged,
        long untagged
) {
}
