package com.careermate.agent.eval;

/**
 * A5：CI 质量门判定——新一次评测的 P95 相较基线跌幅超过阈值则阻塞（返回 false）。
 * CI 在 agent-eval 后调用本判定决定是否红。
 */
public final class EvalGate {

    private EvalGate() {
    }

    /**
     * @param baselineP95 基线 P95
     * @param currentP95  本次 P95
     * @param maxDropRatio 允许的最大跌幅（0.1 = 10%）
     * @return true=通过（未超跌幅）
     */
    public static boolean passes(double baselineP95, double currentP95, double maxDropRatio) {
        if (baselineP95 <= 0) {
            return true; // 无有效基线不阻塞
        }
        double threshold = baselineP95 * (1.0 - maxDropRatio);
        return currentP95 >= threshold;
    }

    public static double dropRatio(double baselineP95, double currentP95) {
        if (baselineP95 <= 0) {
            return 0.0;
        }
        return Math.max(0.0, (baselineP95 - currentP95) / baselineP95);
    }
}
