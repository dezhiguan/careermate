package com.careermate.agent.reflect;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A3 反思闭环配置。
 */
@Data
@ConfigurationProperties(prefix = "careermate.agent.reflection")
public class ReflectionProperties {

    /** 是否启用反思闭环（仅 deep-path 生效）。 */
    private boolean enabled = false;
    /** 硬上限轮数。 */
    private int maxRounds = 3;
    /** planner 温度（拆解偏确定）。 */
    private double plannerTemperature = 0.3;
    /** reflector 温度（审视偏保守）。 */
    private double reflectorTemperature = 0.1;
    /**
     * reflector 模型；留空则用默认 provider 模型。跨家防 self-bias 需另配第二 provider（当前单 provider 下
     * 以低温度 + 独立 system prompt 近似），此项预留切换钩子。
     */
    private String reflectorModel = "";
    /** 成本护栏：反思相较无反思的 token 增幅上限（超则告警）。 */
    private double maxCostMultiplier = 2.5;
}
