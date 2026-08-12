package com.careermate.pipeline.dto;

import lombok.Data;

/**
 * 收藏一个 JD 进暂存区（按 jdDocId 幂等）。
 */
@Data
public class SaveJobRequest {
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
            using = com.careermate.common.support.JdDocIdDeserializer.class)
    private Long jdDocId;
    private String company;
    private String roleTitle;
}
