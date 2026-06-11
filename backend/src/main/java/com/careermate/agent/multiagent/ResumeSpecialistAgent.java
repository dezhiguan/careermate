package com.careermate.agent.multiagent;

import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.AgentToolExecutionService;
import com.careermate.agent.tool.AgentToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeSpecialistAgent {

    private static final Set<String> RESUME_TOOLS =
        Set.of("get_default_resume", "search_knowledge_base", "generate_resume_from_jd");

    private final AgentToolExecutionService toolExecutionService;

    public SpecialistResult process(AgentToolContext context, String userMessage) {
        try {
            String toolName = pickTool(userMessage);
            AgentToolResult result = toolExecutionService.execute(context, toolName);
            if (result.isSuccess()) {
                return SpecialistResult.withTool(AgentDomain.RESUME, toolName, result.getSummary());
            }
            String reason = result.getErrorMessage() != null && !result.getErrorMessage().isBlank()
                    ? result.getErrorMessage()
                    : result.getSummary();
            return SpecialistResult.failed(AgentDomain.RESUME, reason);
        } catch (Exception e) {
            log.warn("ResumeSpecialistAgent failed: {}", e.getMessage());
            return SpecialistResult.failed(AgentDomain.RESUME, e.getMessage());
        }
    }

    private String pickTool(String message) {
        if (message != null) {
            if (shouldGenerateResumeFromJd(message)) {
                return "generate_resume_from_jd";
            }
            if (message.contains("搜索") || message.contains("推荐")
                    || message.contains("建议") || message.contains("知识库")) {
                return "search_knowledge_base";
            }
        }
        return "get_default_resume";
    }

    static boolean shouldGenerateResumeFromJd(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase();
        if (containsAny(lower, "生成简历", "重写简历", "改简历", "优化简历", "按jd", "按 jd",
                "pdf简历", "pdf 简历", "下载pdf", "下载 pdf", "帮我改", "定制简历")) {
            return true;
        }
        return lower.contains("简历")
                && containsAny(lower, "生成", "重写", "改", "优化", "定制", "pdf", "下载");
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
