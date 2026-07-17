package com.careermate.pipeline.dto;

import lombok.Data;

/**
 * 看板卡片改名（displayName 空则回退自动生成名）。
 */
@Data
public class UpdateNameRequest {
    private String displayName;
}
