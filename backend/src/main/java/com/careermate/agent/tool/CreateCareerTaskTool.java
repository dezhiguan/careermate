package com.careermate.agent.tool;

import com.careermate.common.exception.BizException;
import com.careermate.task.CareerTaskConstants;
import com.careermate.task.service.CareerTaskService;
import com.careermate.task.dto.CareerTaskCreateRequest;
import com.careermate.task.dto.CareerTaskResponse;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@lombok.extern.slf4j.Slf4j
public class CreateCareerTaskTool implements AgentTool {

    private final CareerTaskService careerTaskService;

    public CreateCareerTaskTool(CareerTaskService careerTaskService) {
        this.careerTaskService = careerTaskService;
    }

    @Override
    public String name() {
        return "create_career_task";
    }

    @Override
    public String description() {
        return "为当前用户创建求职任务";
    }

    @Override
    public AgentToolDefinition definition() {
        return AgentToolDefinition.base(
                name(),
                "创建求职任务",
                description(),
                AgentToolDomain.TASK,
                AgentToolPermission.WRITE_USER_DATA,
                AgentToolRiskLevel.MEDIUM
        )
                .parameter(AgentToolDefinitionSupport.stringParam(
                        "title", true, "任务标题"))
                .parameter(AgentToolDefinitionSupport.stringParam(
                        "dueDate", false, "截止日期，格式 yyyy-MM-dd"))
                .example("帮我创建一个任务：完善简历")
                .build();
    }

    @Override
    public boolean supports(AgentToolContext context) {
        return true;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> args = context.getArgs();
        String title = stringArg(args, "title", null);
        if (title == null || title.isBlank()) {
            return AgentToolResult.failure(name(), "创建任务失败", "任务标题不能为空");
        }

        CareerTaskCreateRequest request = new CareerTaskCreateRequest();
        request.setTitle(title.trim());
        request.setDescription(stringArg(args, "description", null));
        request.setCategory(stringArg(args, "category", CareerTaskConstants.CATEGORY_GENERAL));
        request.setPriority(stringArg(args, "priority", CareerTaskConstants.PRIORITY_MEDIUM));
        request.setDueDate(parseDueDate(args.get("dueDate")));

        try {
            CareerTaskResponse created = careerTaskService.createTaskForAgent(context.getUserId(), request);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("taskId", created.getId());
            data.put("title", created.getTitle());
            String summary = "已创建任务：" + created.getTitle();
            return AgentToolResult.success(name(), summary, data);
        } catch (BizException e) {
            return AgentToolResult.failure(name(), "创建任务失败", e.getMessage());
        }
    }

    /**
     * 解析截止日期，容忍用户口语。
     *
     * <p>用户说的是「周五前补完」，意图路由就会原样把「周五」塞进 dueDate，
     * {@code LocalDate.parse} 直接抛 {@code Text '周五' could not be parsed}，整个建任务动作失败。
     * 截止日期本就是选填项，不该因为一个说法不标准就让主动作失败——认得出就折算成具体日期，
     * 认不出就当作没填，任务照建。
     */
    private LocalDate parseDueDate(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ignored) {
            LocalDate relative = parseRelativeDate(text);
            if (relative == null) {
                log.info("无法解析截止日期「{}」，按未填写处理", text);
            }
            return relative;
        }
    }

    /** 口语日期 → 具体日期。认不出返回 null（当作没填）。 */
    private LocalDate parseRelativeDate(String text) {
        String t = text.replaceAll("[\\s前底末]", "");
        LocalDate today = LocalDate.now();
        if (t.contains("今天") || t.contains("今日")) {
            return today;
        }
        if (t.contains("明天") || t.contains("明日")) {
            return today.plusDays(1);
        }
        if (t.contains("后天")) {
            return today.plusDays(2);
        }
        if (t.contains("本月")) {
            return today.withDayOfMonth(today.lengthOfMonth());
        }
        Matcher days = Pattern.compile("(\\d+)天[内后]?").matcher(t);
        if (days.find()) {
            return today.plusDays(Long.parseLong(days.group(1)));
        }
        DayOfWeek target = WEEKDAYS.get(weekdayKey(t));
        if (target == null) {
            return null;
        }
        // 「下周三」跨到下一周，「周三/本周三」取本周内该天；已过则顺延到下周同一天
        LocalDate base = t.contains("下周") || t.contains("下星期")
                ? today.plusWeeks(1)
                : today;
        LocalDate candidate = base.with(java.time.temporal.TemporalAdjusters.nextOrSame(target));
        return candidate.isBefore(today) ? candidate.plusWeeks(1) : candidate;
    }

    private static String weekdayKey(String t) {
        Matcher m = Pattern.compile("[周星期]期?([一二三四五六日天1-7])").matcher(t);
        return m.find() ? m.group(1) : "";
    }

    private static final Map<String, DayOfWeek> WEEKDAYS = Map.ofEntries(
            Map.entry("一", DayOfWeek.MONDAY), Map.entry("1", DayOfWeek.MONDAY),
            Map.entry("二", DayOfWeek.TUESDAY), Map.entry("2", DayOfWeek.TUESDAY),
            Map.entry("三", DayOfWeek.WEDNESDAY), Map.entry("3", DayOfWeek.WEDNESDAY),
            Map.entry("四", DayOfWeek.THURSDAY), Map.entry("4", DayOfWeek.THURSDAY),
            Map.entry("五", DayOfWeek.FRIDAY), Map.entry("5", DayOfWeek.FRIDAY),
            Map.entry("六", DayOfWeek.SATURDAY), Map.entry("6", DayOfWeek.SATURDAY),
            Map.entry("日", DayOfWeek.SUNDAY), Map.entry("天", DayOfWeek.SUNDAY),
            Map.entry("7", DayOfWeek.SUNDAY));

    private String stringArg(Map<String, Object> args, String key, String defaultValue) {
        if (args == null || !args.containsKey(key) || args.get(key) == null) {
            return defaultValue;
        }
        String value = String.valueOf(args.get(key)).trim();
        return value.isEmpty() ? defaultValue : value;
    }
}
