package com.careermate.pipeline.dto;

import lombok.Data;

/**
 * 按 JD 一键确认流转阶段（Layer-2 确认卡的「确认」按钮）。
 */
@Data
public class ConfirmStageRequest {
    /** 目标 JD 的文档 id（数字）。 */
    private Long jdDocId;
    /** 目标阶段码。 */
    private String stage;
    /** 可选：公司名（新建卡时用）。 */
    private String company;
    /** 可选：岗位名（新建卡时用）。 */
    private String roleTitle;
}
