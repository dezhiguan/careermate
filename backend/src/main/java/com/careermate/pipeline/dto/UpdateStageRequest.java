package com.careermate.pipeline.dto;

import lombok.Data;

/**
 * 移动看板卡片阶段（拖拽 / 一键确认）。
 */
@Data
public class UpdateStageRequest {
    private String stage;
}
