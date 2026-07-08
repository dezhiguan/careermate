package com.careermate.agent.debate;

/**
 * B1：Critic 对一稿的结构化评审。satisfaction 用于收敛判定；criticism/suggestion 反馈给 Writer 改稿。
 * 注意：satisfaction 属内部信号，不透传给用户，也不喂给 Writer（避免 Writer 迎合分数而非改质量）。
 */
public record CriticVerdict(double satisfaction, String criticism, String suggestion) {

    public static CriticVerdict neutral() {
        return new CriticVerdict(0.5, "", "");
    }
}
