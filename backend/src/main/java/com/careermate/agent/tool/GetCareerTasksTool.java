package com.careermate.agent.tool;

import com.careermate.task.CareerTaskService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GetCareerTasksTool implements AgentTool {

    private final CareerTaskService careerTaskService;

    public GetCareerTasksTool(CareerTaskService careerTaskService) {
        this.careerTaskService = careerTaskService;
    }

    @Override
    public String name() {
        return "get_career_tasks";
    }

    @Override
    public String description() {
        return "查询当前用户最近未完成的求职任务";
    }

    @Override
    public boolean supports(AgentToolContext context) {
        return true;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        List<Map<String, Object>> tasks = careerTaskService.listAgentTodoTasksForUser(context.getUserId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tasks", tasks);
        String summary = String.format("当前有 %d 条未完成任务", tasks.size());
        return AgentToolResult.success(name(), summary, data);
    }
}
