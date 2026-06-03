package com.careermate.agent.tool;

import org.springframework.stereotype.Service;

@Service
public class AgentToolExecutionService {

    private final AgentToolRegistry registry;

    public AgentToolExecutionService(AgentToolRegistry registry) {
        this.registry = registry;
    }

    public AgentToolResult execute(AgentToolContext context, String toolName) {
        return registry.findByName(toolName)
                .map(tool -> {
                    try {
                        return tool.execute(context);
                    } catch (Exception e) {
                        return AgentToolResult.failure(
                                toolName,
                                "工具执行失败",
                                e.getMessage() == null ? "未知错误" : e.getMessage()
                        );
                    }
                })
                .orElse(AgentToolResult.failure(toolName, "未知工具", "tool not found: " + toolName));
    }

    public String startSummary(String toolName) {
        return switch (toolName) {
            case "get_default_resume" -> "正在读取默认简历";
            case "get_latest_job_match" -> "正在读取最近岗位匹配";
            case "create_job_match" -> "正在创建岗位匹配";
            case "create_interview_session" -> "正在创建面试训练";
            case "get_dashboard_overview" -> "正在读取求职看板概览";
            default -> "正在执行 " + toolName;
        };
    }
}
