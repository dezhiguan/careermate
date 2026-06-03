package com.careermate.jobmatch;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class JobMatchAnalyzer {

    private static final List<String> SKILL_KEYWORDS = List.of(
            "Java",
            "Spring Boot",
            "Spring Cloud",
            "MyBatis",
            "PostgreSQL",
            "MySQL",
            "Redis",
            "Elasticsearch",
            "Docker",
            "Kubernetes",
            "RocketMQ",
            "Kafka",
            "RAG",
            "向量检索",
            "AI",
            "Agent",
            "Vue",
            "TypeScript"
    );

    private static final List<String> EXPERIENCE_KEYWORDS = List.of(
            "项目经验",
            "项目经历",
            "负责",
            "架构",
            "开发经验",
            "工作经验",
            "落地",
            "优化",
            "性能"
    );

    public JobMatchAnalysisResult analyze(String resumeContent, String jdContent, String jobTitle) {
        String resume = normalize(resumeContent);
        String jd = normalize(jdContent);
        String title = jobTitle == null ? "" : jobTitle.trim();

        List<String> jdSkills = detectSkills(jd);
        List<String> resumeSkills = detectSkills(resume);
        List<String> matched = jdSkills.stream().filter(resumeSkills::contains).toList();
        List<String> missing = jdSkills.stream().filter(s -> !resumeSkills.contains(s)).toList();

        int score = calculateScore(jd, resume, jdSkills, matched);
        String level = toMatchLevel(score);

        return JobMatchAnalysisResult.builder()
                .matchScore(score)
                .matchLevel(level)
                .matchedSkills(matched)
                .missingSkills(missing)
                .strengths(buildStrengths(matched))
                .risks(buildRisks(missing))
                .suggestions(buildSuggestions(missing, title))
                .analysisSummary(buildSummary(title, score, level, matched, missing))
                .build();
    }

    private List<String> detectSkills(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        Set<String> found = new LinkedHashSet<>();
        for (String skill : SKILL_KEYWORDS) {
            if (containsSkill(lower, text, skill)) {
                found.add(skill);
            }
        }
        return new ArrayList<>(found);
    }

    private boolean containsSkill(String lowerText, String originalText, String skill) {
        if (skill.chars().anyMatch(c -> c > 127)) {
            return originalText.contains(skill);
        }
        return lowerText.contains(skill.toLowerCase(Locale.ROOT));
    }

    private int calculateScore(String jd, String resume, List<String> jdSkills, List<String> matched) {
        int base;
        if (jdSkills.isEmpty()) {
            base = 50;
        } else {
            base = (int) Math.round((matched.size() * 100.0) / jdSkills.size());
        }

        int bonus = 0;
        for (String keyword : EXPERIENCE_KEYWORDS) {
            if (resume.contains(keyword) && jd.contains(keyword)) {
                bonus += 2;
            }
        }
        if (resume.length() > 200 && jd.length() > 100) {
            bonus += 3;
        }

        return Math.max(0, Math.min(100, base + bonus));
    }

    private String toMatchLevel(int score) {
        if (score >= 80) {
            return "HIGH";
        }
        if (score >= 60) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private List<String> buildStrengths(List<String> matched) {
        List<String> strengths = new ArrayList<>();
        if (matched.isEmpty()) {
            strengths.add("简历与岗位 JD 的直接技能重合较少，可结合项目经历补充说明相关能力。");
            return strengths;
        }
        int limit = Math.min(4, matched.size());
        for (int i = 0; i < limit; i++) {
            strengths.add("掌握 " + matched.get(i) + "，与岗位要求一致，可在项目经历中突出相关落地成果。");
        }
        if (matched.size() >= 2) {
            strengths.add("技术栈与 JD 多项核心技能匹配，适合作为投递重点岗位。");
        }
        return strengths.stream().distinct().limit(4).toList();
    }

    private List<String> buildRisks(List<String> missing) {
        List<String> risks = new ArrayList<>();
        if (missing.isEmpty()) {
            risks.add("JD 中可识别的核心技能已基本覆盖，需关注业务场景与团队文化契合度。");
            return risks;
        }
        int limit = Math.min(4, missing.size());
        for (int i = 0; i < limit; i++) {
            risks.add("JD 要求 " + missing.get(i) + "，简历中未明确体现，面试时可能被追问。");
        }
        return risks;
    }

    private List<String> buildSuggestions(List<String> missing, String jobTitle) {
        List<String> suggestions = new ArrayList<>();
        if (!jobTitle.isBlank()) {
            suggestions.add("针对「" + jobTitle + "」岗位，在简历项目描述中补充与 JD 一致的业务关键词。");
        }
        for (String skill : missing.stream().limit(3).toList()) {
            suggestions.add("补充 " + skill + " 相关项目或学习经历，并在技能栏中显式列出。");
        }
        suggestions.add("将最匹配 JD 的 1-2 个项目置顶，并用量化指标描述你的贡献。");
        suggestions.add("投递前对照 JD 逐条自检必备技能，准备 1 分钟口头说明未覆盖项的学习计划。");
        if (missing.isEmpty()) {
            suggestions.add("可进一步研究公司业务，准备与岗位场景结合的系统设计或业务案例。");
        }
        return suggestions.stream().distinct().limit(5).toList();
    }

    private String buildSummary(String jobTitle, int score, String level, List<String> matched, List<String> missing) {
        String levelText = switch (level) {
            case "HIGH" -> "高度匹配";
            case "MEDIUM" -> "中等匹配";
            default -> "匹配度偏低";
        };
        String titlePart = jobTitle.isBlank() ? "目标岗位" : "「" + jobTitle + "」";
        String matchedPart = matched.isEmpty() ? "暂无显著技能重合" : "命中技能包括 " + String.join("、", matched);
        String missingPart = missing.isEmpty() ? "未发现明显技能缺口" : "建议补齐 " + String.join("、", missing);
        String summary = "基于默认简历与岗位 JD 的规则分析，" + titlePart + " 综合匹配分为 "
                + score + " 分（" + levelText + "）。" + matchedPart + "；"
                + missingPart + "。建议在投递前按 suggestions 优化简历表述，并准备相关项目的深度讲解。";
        if (summary.length() > 300) {
            return summary.substring(0, 297) + "...";
        }
        if (summary.length() < 100) {
            return summary + " 可继续完善项目细节与量化成果，以提升岗位竞争力。";
        }
        return summary;
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }
}
