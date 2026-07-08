package com.careermate.agent.debate;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** B1 Writer-Critic debate 配置。 */
@Data
@ConfigurationProperties(prefix = "careermate.agent.debate")
public class DebateProperties {

    /** 是否启用 debate（简历定制走多轮收敛）。 */
    private boolean enabled = false;
    /** 硬上限轮数（设计要求 ≤5）。 */
    private int maxRounds = 3;
    /** 共识阈值：critic satisfaction ≥ 此值即收敛。 */
    private double consensusThreshold = 0.85;
    /** writer 温度（偏创造）。 */
    private double writerTemperature = 0.6;
    /** critic 温度（偏严格）。 */
    private double criticTemperature = 0.2;
}
