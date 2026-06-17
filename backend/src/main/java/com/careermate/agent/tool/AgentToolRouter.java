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
    private static final String TOOL_GET_CAREER_TASKS = "get_career_tasks";
    private static final String TOOL_CREATE_CAREER_TASK = "create_career_task";
    private static final String TOOL_MARK_CAREER_TASK_DONE = "mark_career_task_done";
    private static final String TOOL_RAG_RETRIEVER = "rag_retriever";
    private static final String TOOL_SEARCH_KNOWLEDGE_BASE = "search_knowledge_base";
    private static final String TOOL_GENERATE_RESUME_FROM_JD = "generate_resume_from_jd";

    private static final Pattern JOB_TITLE_PATTERN = Pattern.compile("(?:岗位|职位)[：:]\\s*([^\\n]+)");
    private static final Pattern COMPANY_PATTERN = Pattern.compile("公司[：:]\\s*([^\\n]+)");
    private static final Pattern CREATE_TASK_COLON = Pattern.compile("(?:帮我)?创建一个任务[：:]\\s*(.+)");
    private static final Pattern ADD_TO_TASK = Pattern.compile("把(.+?)加入任务");
    private static final Pattern REMIND_ME = Pattern.compile("提醒我(.+)");
    private static final Pattern NEXT_STEP_DO = Pattern.compile("下一步我要做(.+)");
    private static final Pattern DONE_BY_KEYWORD = Pattern.compile("(.+?)已经做完了");
    private static final Pattern MARK_DONE = Pattern.compile("把(.+?)标记完成");

    public Optional<RoutedTool> route(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return Optional.empty();
        }
        String text = userMessage.trim();
        String lower = text.toLowerCase(Locale.ROOT);

        if (com.careermate.agent.multiagent.ResumeSpecialistAgent.shouldGenerateResumeFromJd(text)
                || containsAny(lower, "帮我改简历")) {
            return Optional.of(new RoutedTool(TOOL_GENERATE_RESUME_FROM_JD, Map.of()));
        }
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
        if (shouldMarkCareerTaskDone(lower, text)) {
            return Optional.of(new RoutedTool(TOOL_MARK_CAREER_TASK_DONE, buildMarkDoneArgs(text)));
        }
        if (shouldCreateCareerTask(lower, text)) {
            return Optional.of(new RoutedTool(TOOL_CREATE_CAREER_TASK, buildCreateCareerTaskArgs(text)));
        }
        if (shouldGetCareerTasks(lower, text)) {
            return Optional.of(new RoutedTool(TOOL_GET_CAREER_TASKS, Map.of()));
        }
        if (containsAny(lower, "求职进展", "看板", "当前状态", "看一下求职")) {
            return Optional.of(new RoutedTool(TOOL_GET_DASHBOARD_OVERVIEW, Map.of()));
        }
        if (shouldRouteRagRetriever(lower, text)) {
            return Optional.of(new RoutedTool(TOOL_RAG_RETRIEVER, buildRagRetrieverArgs(text)));
        }
        if (containsAny(lower, "搜索知识库", "查找相关岗位", "找找类似jd",
                        "行业参考", "类似岗位要求", "搜一下行业")) {
            return Optional.of(new RoutedTool(TOOL_SEARCH_KNOWLEDGE_BASE, Map.of()));
        }
        return Optional.empty();
    }

    private boolean shouldRouteRagRetriever(String lower, String text) {
        if (containsAny(lower, "面试题", "面试问答", "面试参考", "查一下", "帮我查")
                && containsAny(lower, "面试", "redis", "缓存", "jvm", "spring", "kafka", "mysql")) {
            return true;
        }
        if (containsAny(lower, "行情", "薪资", "市场", "招聘趋势", "就业市场")
                && containsAny(lower, "怎么样", "如何", "多少", "趋势", "行情")) {
            return true;
        }
        if (containsAny(lower, "jd", "岗位", "招聘要求", "任职要求", "岗位职责")
                && containsAny(lower, "最关键", "关键能力", "核心能力", "哪些能力", "重点")) {
            return true;
        }
        return containsAny(lower, "rag检索", "知识检索", "检索知识库");
    }

    private Map<String, Object> buildRagRetrieverArgs(String text) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("query", text.length() > 200 ? text.substring(0, 200) : text);
        String lower = text.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "面试题", "面试问答", "面试参考") || lower.contains("面试")) {
            args.put("scene", "INTERVIEW");
        } else if (containsAny(lower, "行情", "薪资", "市场", "招聘趋势", "就业市场")) {
            args.put("scene", "MARKET");
        } else if (containsAny(lower, "jd", "岗位", "招聘要求", "任职要求", "岗位职责")) {
            args.put("scene", "OPPORTUNITY");
        } else {
            args.put("scene", "GENERAL");
        }
        return args;
    }

    private boolean shouldGetCareerTasks(String lower, String text) {
        return containsAny(lower, "我的任务", "下一步任务", "我还有哪些任务", "求职任务清单");
    }

    private boolean shouldCreateCareerTask(String lower, String text) {
        if (containsAny(lower, "帮我创建一个任务", "加入任务", "提醒我", "下一步我要做")) {
            return true;
        }
        return CREATE_TASK_COLON.matcher(text).find()
                || ADD_TO_TASK.matcher(text).find()
                || REMIND_ME.matcher(text).find()
                || NEXT_STEP_DO.matcher(text).find();
    }

    private boolean shouldMarkCareerTaskDone(String lower, String text) {
        if (containsAny(lower, "完成这个任务", "标记完成", "已经做完了")) {
            return true;
        }
        return DONE_BY_KEYWORD.matcher(text).find() || MARK_DONE.matcher(text).find();
    }

    private Map<String, Object> buildCreateCareerTaskArgs(String text) {
        Map<String, Object> args = new LinkedHashMap<>();
        String title = extractCreateTaskTitle(text);
        if (title != null && !title.isBlank()) {
            args.put("title", title.trim());
        }
        return args;
    }

    private String extractCreateTaskTitle(String text) {
        Matcher colon = CREATE_TASK_COLON.matcher(text);
        if (colon.find()) {
            return colon.group(1).trim();
        }
        Matcher add = ADD_TO_TASK.matcher(text);
        if (add.find()) {
            return add.group(1).trim();
        }
        Matcher remind = REMIND_ME.matcher(text);
        if (remind.find()) {
            return remind.group(1).trim();
        }
        Matcher next = NEXT_STEP_DO.matcher(text);
        if (next.find()) {
            return next.group(1).trim();
        }
        int idx = text.indexOf("创建一个任务");
        if (idx >= 0) {
            String tail = text.substring(idx + "创建一个任务".length()).trim();
            if (tail.startsWith("：") || tail.startsWith(":")) {
                return tail.substring(1).trim();
            }
        }
        return null;
    }

    private Map<String, Object> buildMarkDoneArgs(String text) {
        Map<String, Object> args = new LinkedHashMap<>();
        String keyword = extractMarkDoneKeyword(text);
        if (keyword != null && !keyword.isBlank()) {
            args.put("titleKeyword", keyword.trim());
        }
        return args;
    }

    private String extractMarkDoneKeyword(String text) {
        Matcher done = DONE_BY_KEYWORD.matcher(text);
        if (done.find()) {
            return done.group(1).trim();
        }
        Matcher mark = MARK_DONE.matcher(text);
        if (mark.find()) {
            return mark.group(1).trim();
        }
        if (text.contains("已经做完了")) {
            int idx = text.indexOf("已经做完了");
            if (idx > 0) {
                return text.substring(0, idx).trim();
            }
        }
        return null;
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
