package com.careermate.pipeline.dto;

import lombok.Data;

/**
 * 创建/开始一个投递机会（点「准备这个岗位」时）。
 */
@Data
public class CreateApplicationRequest {
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
            using = com.careermate.common.support.JdDocIdDeserializer.class)
    private Long jdDocId;
    private String company;
    private String roleTitle;
    private String resumeVersionId;
    private String notes;
    /** 可选初始阶段；缺省 PREPARING。 */
    private String stage;
}
