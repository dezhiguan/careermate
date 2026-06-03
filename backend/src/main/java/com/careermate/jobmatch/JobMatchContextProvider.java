package com.careermate.jobmatch;

import com.careermate.model.entity.JobMatchEntity;
import com.careermate.security.CurrentUserContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class JobMatchContextProvider {

    private static final String EMPTY_CONTEXT_TEXT = "当前用户暂无岗位匹配记录。";
    private static final int MAX_JOB_MATCH_CONTEXT_CHARS = 4000;
    private static final String TRUNCATED_SUFFIX = "（岗位匹配上下文已截断，仅展示前 4000 字符）";

    private final JobMatchService jobMatchService;
    private final JobMatchJsonSupport jobMatchJsonSupport;

    public JobMatchContextProvider(JobMatchService jobMatchService, JobMatchJsonSupport jobMatchJsonSupport) {
        this.jobMatchService = jobMatchService;
        this.jobMatchJsonSupport = jobMatchJsonSupport;
    }

    public JobMatchContext getCurrentUserLatestJobMatchContext() {
        return getLatestJobMatchContext(CurrentUserContext.getUserId());
    }

    public JobMatchContext getLatestJobMatchContext(Long userId) {
        if (userId == null) {
            return emptyContext();
        }
        Optional<JobMatchEntity> matchOpt = jobMatchService.getLatestActiveMatch(userId);
        if (matchOpt.isEmpty()) {
            return emptyContext();
        }
        return toContext(matchOpt.get());
    }

    private JobMatchContext toContext(JobMatchEntity entity) {
        List<String> matchedSkills = jobMatchJsonSupport.readStringList(entity.getMatchedSkills());
        List<String> missingSkills = jobMatchJsonSupport.readStringList(entity.getMissingSkills());
        List<String> strengths = jobMatchJsonSupport.readStringList(entity.getStrengths());
        List<String> risks = jobMatchJsonSupport.readStringList(entity.getRisks());
        List<String> suggestions = jobMatchJsonSupport.readStringList(entity.getSuggestions());

        String contextText = buildContextText(
                entity.getJobTitle(),
                entity.getCompanyName(),
                entity.getMatchScore(),
                entity.getMatchLevel(),
                matchedSkills,
                missingSkills,
                strengths,
                risks,
                suggestions,
                entity.getAnalysisSummary()
        );

        return JobMatchContext.builder()
                .available(true)
                .jobMatchId(entity.getId())
                .resumeId(entity.getResumeId())
                .jobTitle(entity.getJobTitle())
                .companyName(entity.getCompanyName())
                .matchScore(entity.getMatchScore())
                .matchLevel(entity.getMatchLevel())
                .matchedSkills(matchedSkills)
                .missingSkills(missingSkills)
                .strengths(strengths)
                .risks(risks)
                .suggestions(suggestions)
                .analysisSummary(entity.getAnalysisSummary())
                .contextText(contextText)
                .build();
    }

    private JobMatchContext emptyContext() {
        return JobMatchContext.builder()
                .available(false)
                .contextText(EMPTY_CONTEXT_TEXT)
                .build();
    }

    private String buildContextText(
            String jobTitle,
            String companyName,
            Integer matchScore,
            String matchLevel,
            List<String> matchedSkills,
            List<String> missingSkills,
            List<String> strengths,
            List<String> risks,
            List<String> suggestions,
            String analysisSummary
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("最近岗位匹配结果：\n");
        sb.append("岗位：").append(nullToEmpty(jobTitle)).append('\n');
        sb.append("公司：").append(formatCompany(companyName)).append('\n');
        sb.append("匹配分数：").append(matchScore == null ? 0 : matchScore).append('\n');
        sb.append("匹配等级：").append(formatMatchLevel(matchLevel)).append('\n');
        sb.append("命中技能：").append(joinOrNone(matchedSkills)).append('\n');
        sb.append("缺失技能：").append(joinOrNone(missingSkills)).append('\n');
        sb.append("优势：\n");
        appendBulletLines(sb, strengths);
        sb.append("风险：\n");
        appendBulletLines(sb, risks);
        sb.append("建议：\n");
        appendBulletLines(sb, suggestions);
        sb.append("总结：\n");
        sb.append(nullToEmpty(analysisSummary));

        return truncateForPrompt(sb.toString().trim());
    }

    private void appendBulletLines(StringBuilder sb, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            sb.append("- 无\n");
            return;
        }
        for (String line : lines) {
            sb.append("- ").append(line).append('\n');
        }
    }

    private String joinOrNone(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "无";
        }
        return items.stream().collect(Collectors.joining("、"));
    }

    private String formatCompany(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            return "未填写";
        }
        return companyName.trim();
    }

    private String formatMatchLevel(String matchLevel) {
        if (matchLevel == null) {
            return "未知";
        }
        return switch (matchLevel) {
            case "HIGH" -> "高度匹配";
            case "MEDIUM" -> "中等匹配";
            case "LOW" -> "偏低";
            default -> matchLevel;
        };
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String truncateForPrompt(String text) {
        if (text.length() <= MAX_JOB_MATCH_CONTEXT_CHARS) {
            return text;
        }
        return text.substring(0, MAX_JOB_MATCH_CONTEXT_CHARS) + "\n" + TRUNCATED_SUFFIX;
    }
}
