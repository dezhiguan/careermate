package com.careermate.pipeline.dto;

import java.time.LocalDateTime;

/**
 * 暂存区一条：收藏但未动的 JD。
 */
public record SavedJobVO(
        Long id,
        Long jdDocId,
        String company,
        String roleTitle,
        LocalDateTime savedAt
) {
}
