package com.careermate.interview;

import com.careermate.model.entity.InterviewQuestionEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class InterviewAnswerEvaluator {

    private final InterviewLlmEvaluator llmEvaluator;

    public InterviewAnswerEvaluator(InterviewLlmEvaluator llmEvaluator) {
        this.llmEvaluator = llmEvaluator;
    }

    public record EvaluationResult(
            int score,
            String feedback,
            List<String> strengths,
            List<String> improvements
    ) {
    }

    public EvaluationResult evaluate(InterviewQuestionEntity question, String answerText, List<String> referencePoints) {
        Optional<EvaluationStructuredResult> llmResult =
            llmEvaluator.tryEvaluate(question, answerText, referencePoints);
        if (llmResult.isPresent()) {
            return toEvaluationResult(llmResult.get());
        }
        return ruleBasedEvaluate(question, answerText, referencePoints);
    }

    private EvaluationResult toEvaluationResult(EvaluationStructuredResult r) {
        return new EvaluationResult(
            r.score(),
            r.feedback(),
            r.strengths() == null ? java.util.List.of() : r.strengths(),
            r.improvements() == null ? java.util.List.of() : r.improvements()
        );
    }

    public EvaluationResult ruleBasedEvaluate(InterviewQuestionEntity question, String answerText, List<String> referencePoints) {
        String trimmed = answerText == null ? "" : answerText.trim();
        int len = trimmed.length();
        int score = baseScoreByLength(len);
        int keywordHits = countKeywordHits(trimmed, referencePoints);
        score = Math.min(100, score + keywordHits * 5);
        score = Math.max(0, score);

        String feedback = buildFeedback(len, score, keywordHits, question.getQuestionType());
        List<String> strengths = buildStrengths(len, score, keywordHits);
        List<String> improvements = buildImprovements(len, score, keywordHits, question.getQuestionType());

        return new EvaluationResult(score, feedback, strengths, improvements);
    }

    private int baseScoreByLength(int len) {
        if (len < 30) {
            return 40;
        }
        if (len <= 120) {
            return 60;
        }
        return 75;
    }

    private int countKeywordHits(String answer, List<String> referencePoints) {
        if (referencePoints == null || referencePoints.isEmpty()) {
            return 0;
        }
        String lower = answer.toLowerCase(Locale.ROOT);
        int hits = 0;
        for (String point : referencePoints) {
            if (point == null || point.isBlank()) {
                continue;
            }
            if (lower.contains(point.toLowerCase(Locale.ROOT))) {
                hits++;
            }
        }
        return hits;
    }

    private String buildFeedback(int len, int score, int keywordHits, String questionType) {
        if (len < 30) {
            return "回答偏短，建议补充项目背景、你的具体职责、技术细节和可量化结果，便于面试官判断深度。";
        }
        if (keywordHits == 0) {
            return "回答有一定结构，但未明显覆盖本题参考要点（如技术名词、职责、结果）。建议对照参考要点逐条展开。";
        }
        if (score >= 80) {
            return "回答较完整，覆盖了部分关键要点，表达相对清晰。可再补充数据指标或对比方案以提升说服力。";
        }
        return "回答具备基础内容，建议结合「" + typeLabel(questionType) + "」类问题，补充案例细节、难点与个人贡献。";
    }

    private List<String> buildStrengths(int len, int score, int keywordHits) {
        List<String> list = new ArrayList<>();
        if (len >= 30) {
            list.add("愿意展开说明，具备基本表达意愿");
        }
        if (len > 120) {
            list.add("回答篇幅较充实，有助于展示思考过程");
        }
        if (keywordHits > 0) {
            list.add("回答中体现了与题目相关的技术或业务关键词");
        }
        if (score >= 75) {
            list.add("整体完成度较好，可作为后续深化练习的基础");
        }
        if (list.isEmpty()) {
            list.add("已提交回答，可在此基础上迭代完善");
        }
        while (list.size() < 2) {
            list.add("保持练习节奏，逐题积累表达模板");
        }
        return list.size() > 3 ? list.subList(0, 3) : list;
    }

    private List<String> buildImprovements(int len, int score, int keywordHits, String questionType) {
        List<String> list = new ArrayList<>();
        if (len < 30) {
            list.add("将回答扩展到至少 3-5 句话，包含背景、行动、结果");
        }
        if (len < 120) {
            list.add("补充具体技术选型、难点与个人职责，避免只给结论");
        }
        if (keywordHits == 0) {
            list.add("对照题目参考要点，逐条写入回答（如技术栈、指标、协作方式）");
        }
        if ("GAP".equals(questionType)) {
            list.add("说明学习计划、实践路径及如何在项目中验证新技能");
        }
        if ("SYSTEM_DESIGN".equals(questionType)) {
            list.add("补充模块划分、存储方案、限流/降级等架构细节");
        }
        if (score < 70) {
            list.add("可使用 STAR 结构（情境-任务-行动-结果）组织行为类与项目类回答");
        }
        if (list.isEmpty()) {
            list.add("尝试加入量化结果（性能、稳定性、效率提升等）");
        }
        while (list.size() < 2) {
            list.add("完成后可对照岗位 JD 再润色一版回答");
        }
        return list.size() > 3 ? list.subList(0, 3) : list;
    }

    private String typeLabel(String questionType) {
        if (questionType == null) {
            return "综合";
        }
        return switch (questionType) {
            case "PROJECT" -> "项目经历";
            case "SKILL" -> "技能落地";
            case "GAP" -> "技能补齐";
            case "SYSTEM_DESIGN" -> "系统设计";
            case "BEHAVIOR" -> "行为面试";
            default -> "综合";
        };
    }
}
