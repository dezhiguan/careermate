package com.careermate.agent.checkpoint;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * B3：Agent 断点续跑（checkpoint）能力开关。默认关，开启后 /api/agent/checkpoint/* 可用。
 */
@Data
@ConfigurationProperties(prefix = "careermate.agent.checkpoint")
public class CheckpointProperties {
    /** 是否启用断点续跑能力（含运行历史/续跑/分叉接口）。 */
    private boolean enabled = false;
    /** 单用户可列出的最近运行数上限。 */
    private int listLimit = 20;
}
