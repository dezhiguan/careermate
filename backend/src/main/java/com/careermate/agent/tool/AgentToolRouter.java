package com.careermate.agent.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AgentToolRouter {

    private static final String TOOL_GET_DEFAULT_RESUME = "get_default_resume";
    private static final String TOOL_GET_LATEST_JOB_MATCH = "get_latest_job_match";
    private static final String TOOL_CREATE_JOB_MATCH = "create_job_match";
    private static final String TOOL_CREATE_INTERVIEW_SESSION = "create_interview_session";
    private static final String TOOL_GET_DASHBOARD_OVERVIEW = "get_dashboard_overview";

    private static final Pattern JOB_TITLE_PATTERN = Pattern.compile("(?:岗位|职位)[：:]\\s*([^\\n]+)");
    private static final Pattern COMPANY_PATTERN = Pattern.compile("公司[：:]\\s*([^\\n]+)");

    public Optional<RoutedTool> route(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return Optional.empty();
        }
        String text = userMessage.trim();
        String lower = text.toLowerCase(Locale.ROOT);

        if (containsAny(lower, "默认简历", "我的简历", "查看简历", "分析简历")) {
            return Optional.of(new RoutedTool(TOOL_GET_DEFAULT_RESUME, Map.of()));
        }
        if (containsAny(lower, "最近岗位", "岗位匹配", "匹配结果", "差距")) {
            return Optional.of(new RoutedTool(TOOL_GET_LATEST_JOB_MATCH, Map.of()));
        }
        if (shouldCreateJobMatch(lower, text)) {
            return Optional.of(new RoutedTool(TOOL_CREATE_JOB_MATCH, buildCreateJobMatchArgs(text)));
        }
        if (containsAny(lower, "生成面试", "创建面试", "面试训练", "准备面试")) {
            return Optional.of(new RoutedTool(TOOL_CREATE_INTERVIEW_SESSION, Map.of()));
        }
        if (containsAny(lower, "求职进展", "看板", "下一步", "当前状态")) {
            return Optional.of(new RoutedTool(TOOL_GET_DASHBOARD_OVERVIEW, Map.of()));
        }
        return Optional.empty();
    }

    private boolean shouldCreateJobMatch(String lower, String text) {
        if (text.length() <= 80) {
            return false;
        }
        if (containsAny(lower, "帮我匹配这个岗位", "分析这个 jd", "分析这个jd", "岗位 jd", "岗位jd", "招聘要求")) {
            return true;
        }
        return looksLikeJd(text);
    }

    private boolean looksLikeJd(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        int signals = 0;
        if (lower.contains("岗位职责") || lower.contains("任职要求") || lower.contains("招聘要求")) {
            signals++;
        }
        if (lower.contains("jd") || lower.contains("job description")) {
            signals++;
        }
        if (JOB_TITLE_PATTERN.matcher(text).find() || COMPANY_PATTERN.matcher(text).find()) {
            signals++;
        }
        if (text.length() > 200) {
            signals++;
        }
        return signals >= 2;
    }

    private Map<String, Object> buildCreateJobMatchArgs(String text) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("jobTitle", extractJobTitle(text));
        args.put("companyName", extractCompanyName(text));
        args.put("jdContent", text);
        return args;
    }

    private String extractJobTitle(String text) {
        Matcher matcher = JOB_TITLE_PATTERN.matcher(text);
        if (matcher.find()) {
            return trimAtMarkers(matcher.group(1), "公司", "招聘");
        }
        return "用户输入岗位";
    }

    private String extractCompanyName(String text) {
        Matcher matcher = COMPANY_PATTERN.matcher(text);
        if (matcher.find()) {
            String company = trimAtMarkers(matcher.group(1), "招聘");
            return company.isEmpty() ? null : company;
        }
        return null;
    }

    private String trimAtMarkers(String value, String... markers) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        int cut = trimmed.length();
        for (String marker : markers) {
            int idx = trimmed.indexOf(marker);
            if (idx >= 0 && idx < cut) {
                cut = idx;
            }
        }
        String result = trimmed.substring(0, cut).trim();
        while (result.endsWith("：") || result.endsWith(":")) {
            result = result.substring(0, result.length() - 1).trim();
        }
        return result;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public record RoutedTool(String toolName, Map<String, Object> args) {
    }
}
