package com.careermate.agent.tool;

import com.careermate.common.exception.BizException;
import com.careermate.model.entity.CareerTaskEntity;
import com.careermate.task.CareerTaskService;
import com.careermate.task.dto.CareerTaskResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MarkCareerTaskDoneTool implements AgentTool {

    private final CareerTaskService careerTaskService;

    public MarkCareerTaskDoneTool(CareerTaskService careerTaskService) {
        this.careerTaskService = careerTaskService;
    }

    @Override
    public String name() {
        return "mark_career_task_done";
    }

    @Override
    public String description() {
        return "将当前用户的求职任务标记为已完成";
    }

    @Override
    public boolean supports(AgentToolContext context) {
        return true;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> args = context.getArgs();
        Long userId = context.getUserId();
        Long taskId = parseTaskId(args.get("taskId"));
        CareerTaskEntity entity = null;

        if (taskId != null) {
            entity = careerTaskService.findTodoTaskForUser(userId, taskId);
        }
        if (entity == null) {
            String keyword = stringArg(args, "titleKeyword", null);
            if (keyword != null && !keyword.isBlank()) {
                entity = careerTaskService.findTodoTaskByTitleKeyword(userId, keyword);
            }
        }
        if (entity == null) {
            return AgentToolResult.failure(name(), "未找到可完成的任务", "请提供更准确的任务标题或 taskId");
        }

        try {
            CareerTaskResponse done = careerTaskService.markDoneForUser(userId, entity.getId());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("taskId", done.getId());
            data.put("title", done.getTitle());
            String summary = "已完成任务：" + done.getTitle();
            return AgentToolResult.success(name(), summary, data);
        } catch (BizException e) {
            return AgentToolResult.failure(name(), "完成任务失败", e.getMessage());
        }
    }

    private Long parseTaskId(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String stringArg(Map<String, Object> args, String key, String defaultValue) {
        if (args == null || !args.containsKey(key) || args.get(key) == null) {
            return defaultValue;
        }
        String value = String.valueOf(args.get(key)).trim();
        return value.isEmpty() ? defaultValue : value;
    }
}
