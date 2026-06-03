package com.careermate.interview;

import com.careermate.jobmatch.JobMatchJsonSupport;
import com.careermate.model.entity.JobMatchEntity;
import com.careermate.model.entity.ResumeEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class InterviewQuestionGenerator {

    private static final Pattern SPLIT_PATTERN = Pattern.compile("[,，、;；\\s\\n]+");
    private static final List<String> COMMON_TECH_KEYWORDS = List.of(
            "Java", "Spring Boot", "Spring", "Redis", "MySQL", "PostgreSQL",
            "Docker", "Kubernetes", "Elasticsearch", "Kafka", "微服务", "分布式",
            "Vue", "React", "Python", "Go", "后端", "前端"
    );

    private final JobMatchJsonSupport jobMatchJsonSupport;

    public InterviewQuestionGenerator(JobMatchJsonSupport jobMatchJsonSupport) {
        this.jobMatchJsonSupport = jobMatchJsonSupport;
    }

    public record GeneratedQuestion(
            int questionNo,
            String questionType,
            String questionText,
            List<String> referencePoints
    ) {
    }

    public List<GeneratedQuestion> generate(ResumeEntity resume, Optional<JobMatchEntity> latestJobMatch) {
        List<String> matchedSkills = latestJobMatch
                .map(m -> jobMatchJsonSupport.readStringList(m.getMatchedSkills()))
                .orElse(List.of());
        List<String> missingSkills = latestJobMatch
                .map(m -> jobMatchJsonSupport.readStringList(m.getMissingSkills()))
                .orElse(List.of());
        List<String> resumeKeywords = extractResumeKeywords(resume.getContent());
        String resumeTitle = resume.getTitle() == null ? "你的简历" : resume.getTitle().trim();
        String jobTitle = latestJobMatch.map(JobMatchEntity::getJobTitle).orElse("目标岗位");

        List<GeneratedQuestion> questions = new ArrayList<>();

        questions.add(new GeneratedQuestion(
                1,
                "PROJECT",
                "请介绍你简历「" + resumeTitle + "」中最能体现后端/技术能力的一个项目，重点说明你的职责、技术难点和结果。",
                mergePoints(resumeKeywords, List.of("项目", "职责", "难点", "结果", "技术"))
        ));

        List<String> skillFocus = matchedSkills.isEmpty() ? resumeKeywords : matchedSkills;
        String skillJoined = joinTop(skillFocus, 3, "Java、Spring Boot");
        questions.add(new GeneratedQuestion(
                2,
                "SKILL",
                latestJobMatch.isPresent()
                        ? "岗位匹配中命中的技能包括 " + skillJoined + "。请选择其中一个，结合项目说明你是如何落地的。"
                        : "请从简历中的技术栈（如 " + skillJoined + "）选一个，结合项目说明你是如何落地实践的。",
                mergePoints(skillFocus, List.of("落地", "实践", "项目", "案例"))
        ));

        if (latestJobMatch.isPresent() && !missingSkills.isEmpty()) {
            String missingJoined = joinTop(missingSkills, 4, "相关技能");
            questions.add(new GeneratedQuestion(
                    3,
                    "GAP",
                    "岗位匹配显示 " + missingJoined + " 是缺失技能。你会如何快速补齐，并在项目中体现相关能力？",
                    mergePoints(missingSkills, List.of("学习计划", "实践", "项目", "补齐"))
            ));
        } else {
            String gapSkill = resumeKeywords.isEmpty() ? "分布式或工程化能力" : resumeKeywords.get(0);
            questions.add(new GeneratedQuestion(
                    3,
                    "GAP",
                    "请说明你当前技术栈中最需要补齐的一项能力（如 " + gapSkill + "），以及你的学习计划。",
                    List.of("学习计划", "实践路径", gapSkill, "项目验证")
            ));
        }

        questions.add(new GeneratedQuestion(
                4,
                "SYSTEM_DESIGN",
                latestJobMatch.isPresent()
                        ? "如果让你为「" + jobTitle + "」相关场景设计一个支持高并发的求职 Agent 会话服务，你会如何设计核心模块、存储和限流？"
                        : "如果让你设计一个支持高并发的求职 Agent 会话服务，你会如何设计核心模块、存储和限流？",
                List.of("模块划分", "存储", "限流", "高并发", "会话", "缓存")
        ));

        questions.add(new GeneratedQuestion(
                5,
                "BEHAVIOR",
                "请讲一次你在项目中遇到困难并推动解决的经历，说明你的角色、采取的行动和最终结果。",
                List.of("困难", "行动", "结果", "协作", "推动", "STAR")
        ));

        return questions;
    }

    private List<String> extractResumeKeywords(String content) {
        Set<String> found = new LinkedHashSet<>();
        if (content != null && !content.isBlank()) {
            for (String keyword : COMMON_TECH_KEYWORDS) {
                if (content.contains(keyword)) {
                    found.add(keyword);
                }
            }
            for (String token : SPLIT_PATTERN.split(content)) {
                String t = token.trim();
                if (t.length() >= 2 && t.length() <= 32 && looksLikeSkill(t)) {
                    found.add(t);
                }
                if (found.size() >= 8) {
                    break;
                }
            }
        }
        if (found.isEmpty()) {
            found.add("Java");
            found.add("Spring Boot");
        }
        return new ArrayList<>(found);
    }

    private boolean looksLikeSkill(String token) {
        if (token.matches(".*[\\u4e00-\\u9fa5].*")) {
            return true;
        }
        return Character.isUpperCase(token.charAt(0)) || token.contains("Boot") || token.length() <= 16;
    }

    private String joinTop(List<String> items, int max, String fallback) {
        if (items == null || items.isEmpty()) {
            return fallback;
        }
        return items.stream().limit(max).reduce((a, b) -> a + "、" + b).orElse(fallback);
    }

    private List<String> mergePoints(List<String> primary, List<String> secondary) {
        Set<String> set = new LinkedHashSet<>();
        if (primary != null) {
            for (String p : primary) {
                if (p != null && !p.isBlank()) {
                    set.add(p.trim());
                }
            }
        }
        if (secondary != null) {
            set.addAll(secondary);
        }
        return new ArrayList<>(set).stream().limit(8).toList();
    }
}
