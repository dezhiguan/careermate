package com.careermate.agent.eval;

import java.util.List;

/**
 * A5：一次评测运行的汇总。
 *
 * @param suiteId      套件 id
 * @param count        场景数
 * @param avgOverall   平均综合分
 * @param p95Overall   综合分 P95（越低越差；CI gate 依据）
 * @param markdown     markdown 报告
 * @param scores       每条 overall 分
 */
public record EvalReport(
        String suiteId,
        int count,
        double avgOverall,
        double p95Overall,
        String markdown,
        List<Double> scores
) {
}
