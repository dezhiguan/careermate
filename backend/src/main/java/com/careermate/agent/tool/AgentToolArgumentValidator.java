package com.careermate.agent.tool;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Optional;

@Component
public class AgentToolArgumentValidator {

    public Optional<AgentToolResult> validate(AgentToolDefinition definition, AgentToolContext context) {
        if (definition == null || context == null) {
            return Optional.of(AgentToolResult.failure("unknown", "参数校验失败", "工具定义或上下文缺失"));
        }
        String toolName = definition.getName();
        Map<String, Object> args = context.getArgs() == null ? Map.of() : context.getArgs();

        if (containsForbiddenIdentityArg(args)) {
            return Optional.of(AgentToolResult.failure(
                    toolName,
                    "参数校验失败",
                    "禁止在工具参数中传递 userId 或 sessionId"
            ));
        }

        for (AgentToolParameter parameter : definition.getParameters()) {
            if (parameter.isRequired() && !hasArgValue(args, parameter.getName())) {
                if (!isSatisfiedByFallback(toolName, parameter.getName(), context)) {
                    return Optional.of(AgentToolResult.failure(
                            toolName,
                            "参数校验失败",
                            "缺少必填参数: " + parameter.getName()
                    ));
                }
            }
        }

        Optional<AgentToolResult> special = validateSpecialRules(toolName, context, args);
        return special;
    }

    private Optional<AgentToolResult> validateSpecialRules(
            String toolName,
            AgentToolContext context,
            Map<String, Object> args
    ) {
        return switch (toolName) {
            case "mark_career_task_done" -> {
                if (!hasArgValue(args, "taskId") && !hasArgValue(args, "titleKeyword")) {
                    yield Optional.of(AgentToolResult.failure(
                            toolName,
                            "参数校验失败",
                            "taskId 与 titleKeyword 至少需要提供一个"
                    ));
                }
                yield Optional.empty();
            }
            case "generate_resume_from_jd" -> {
                if (!StringUtils.hasText(context.getSessionId())) {
                    yield Optional.of(AgentToolResult.failure(
                            toolName,
                            "参数校验失败",
                            "缺少 sessionId 上下文"
                    ));
                }
                yield Optional.empty();
            }
            default -> Optional.empty();
        };
    }

    private boolean isSatisfiedByFallback(String toolName, String parameterName, AgentToolContext context) {
        if ("create_job_match".equals(toolName) && "jdContent".equals(parameterName)) {
            return hasArgValue(context.getArgs(), "jdContent")
                    || StringUtils.hasText(context.getUserMessage());
        }
        return false;
    }

    private boolean containsForbiddenIdentityArg(Map<String, Object> args) {
        return args.containsKey("userId") || args.containsKey("sessionId");
    }

    private boolean hasArgValue(Map<String, Object> args, String key) {
        if (args == null || !args.containsKey(key)) {
            return false;
        }
        Object value = args.get(key);
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            return StringUtils.hasText(text);
        }
        return true;
    }
}
