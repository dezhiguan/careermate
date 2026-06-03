package com.careermate.agent.tool;

import com.careermate.common.exception.BizException;
import com.careermate.task.CareerTaskConstants;
import com.careermate.task.CareerTaskService;
import com.careermate.task.dto.CareerTaskCreateRequest;
import com.careermate.task.dto.CareerTaskResponse;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
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

    private LocalDate parseDueDate(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        return LocalDate.parse(text);
    }

    private String stringArg(Map<String, Object> args, String key, String defaultValue) {
        if (args == null || !args.containsKey(key) || args.get(key) == null) {
            return defaultValue;
        }
        String value = String.valueOf(args.get(key)).trim();
        return value.isEmpty() ? defaultValue : value;
    }
}
