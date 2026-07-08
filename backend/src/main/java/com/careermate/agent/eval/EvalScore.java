package com.careermate.agent.eval;

/**
 * A5：单条评测的 facet 打分（0-1）。
 *
 * @param relevance           相关性
 * @param correctness         正确性
 * @param citationFaithfulness 引用忠实度/语料支撑度（答案是否真被知识库命中内容支撑、有无编造）
 * @param overall             综合分
 */
public record EvalScore(
        double relevance,
        double correctness,
        double citationFaithfulness,
        double overall
) {
    /** 解析失败时的中性兜底分。 */
    public static EvalScore neutral() {
        return new EvalScore(0.5, 0.5, 0.5, 0.5);
    }
}
