package com.careermate.agent.eval;

import com.careermate.mapper.EvalResultMapper;
import com.careermate.model.entity.EvalResultEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A5：评测运行器。对每个 scenario 取答案 → LLM-as-judge 打分 → 落 eval_results，
 * 汇总平均分与 P95（CI gate 依据）并出 markdown 报告。
 */
@Slf4j
@Service
public class EvalRunner {

    private final AnswerJudge answerJudge;
    private final EvalResultMapper evalResultMapper;
    private final ObjectMapper objectMapper;

    public EvalRunner(AnswerJudge answerJudge, EvalResultMapper evalResultMapper, ObjectMapper objectMapper) {
        this.answerJudge = answerJudge;
        this.evalResultMapper = evalResultMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * @param modelProvider 被评 provider（用于打标；answerFn 内部决定实际调用）
     * @param judgeModel    judge 模型（跨家避偏，留空用默认）
     * @param answerFn      给定 scenario 产出答案
     */
    public EvalReport run(String suiteId, List<EvalScenario> scenarios, String modelProvider,
                          String judgeModel, Function<EvalScenario, String> answerFn) {
        List<Double> overalls = new ArrayList<>();
        StringBuilder md = new StringBuilder("# Eval 报告 · ").append(suiteId).append("\n\n")
                .append("| scenario | scene | relevance | correctness | citation | overall |\n")
                .append("|---|---|---|---|---|---|\n");

        for (EvalScenario s : scenarios) {
            String answer;
            try {
                answer = answerFn.apply(s);
            } catch (Exception e) {
                log.warn("scenario {} 取答案失败，记 0 分: {}", s.id(), e.getMessage());
                answer = "";
            }
            EvalScore score = answerJudge.judge(s.question(), answer, s.criteria(), judgeModel);
            overalls.add(score.overall());
            persist(suiteId, s, modelProvider, judgeModel, score);
            md.append("| ").append(s.id()).append(" | ").append(s.scene()).append(" | ")
                    .append(fmt(score.relevance())).append(" | ").append(fmt(score.correctness())).append(" | ")
                    .append(fmt(score.citationFaithfulness())).append(" | ").append(fmt(score.overall())).append(" |\n");
        }

        double avg = overalls.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double p95 = percentile(overalls, 95);
        md.append("\n平均 overall = ").append(fmt(avg)).append("，P95 = ").append(fmt(p95)).append('\n');
        return new EvalReport(suiteId, scenarios.size(), avg, p95, md.toString(), overalls);
    }

    private void persist(String suiteId, EvalScenario s, String modelProvider, String judgeModel, EvalScore score) {
        try {
            EvalResultEntity e = new EvalResultEntity();
            e.setEvalSuiteId(suiteId);
            e.setScenarioId(s.id());
            e.setModelProvider(modelProvider);
            e.setJudgeProvider(judgeModel == null || judgeModel.isBlank() ? "default" : judgeModel);
            e.setJudgeScores(objectMapper.writeValueAsString(Map.of(
                    "relevance", score.relevance(),
                    "correctness", score.correctness(),
                    "citation_faithfulness", score.citationFaithfulness(),
                    "overall", score.overall())));
            e.setOverallScore(score.overall());
            evalResultMapper.insert(e);
        } catch (Exception ex) {
            log.warn("eval 结果落库失败: {}", ex.getMessage());
        }
    }

    /** P95：分数升序后取第 95 百分位（越低代表越差的尾部）。空集返回 0。 */
    static double percentile(List<Double> values, int p) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        // 用"最差 P95"语义：这里对 overall 分，通常关注低分尾部，取 (100-p) 分位的低分
        int idx = (int) Math.ceil((p / 100.0) * sorted.size()) - 1;
        idx = Math.max(0, Math.min(sorted.size() - 1, idx));
        return sorted.get(idx);
    }

    private String fmt(double v) {
        return String.format("%.3f", v);
    }
}
