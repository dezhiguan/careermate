package com.careermate.agent.multiagent;

import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.AgentToolExecutionService;
import com.careermate.agent.tool.AgentToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeSpecialistAgent implements SpecialistAgent {

    private final AgentToolExecutionService toolExecutionService;

    @Override
    public AgentDomain domain() {
        return AgentDomain.RESUME;
    }

    @Override
    public String agentName() {
        return "ResumeSpecialistAgent";
    }

    public SpecialistResult process(AgentToolContext context, String userMessage) {
        return process(context, userMessage, new AgentSupervisorRoute(
                java.util.List.of(AgentDomain.RESUME),
                AgentDomain.RESUME,
                1D,
                "LEGACY_SINGLE",
                false
        ));
    }

    @Override
    public SpecialistResult process(AgentToolContext context, String userMessage, AgentSupervisorRoute route) {
        try {
            if (route != null && route.requiresCritic() && FabricationRiskDetector.detect(userMessage)) {
                return SpecialistResult.builder()
                        .domain(AgentDomain.RESUME)
                        .agentName(agentName())
                        .intent("RESUME_WRITE_BLOCKED")
                        .status(SpecialistResultStatus.NO_ACTION)
                        .riskLevel(SpecialistRiskLevel.HIGH)
                        .summary("检测到简历造假风险，已交由审查专家处理，不执行写简历工具。")
                        .build();
            }
            String toolName = pickTool(userMessage);
            AgentToolResult result = toolExecutionService.execute(context, toolName);
            if (result.isSuccess()) {
                return SpecialistResult.builder()
                        .domain(AgentDomain.RESUME)
                        .agentName(agentName())
                        .intent(intentForTool(toolName))
                        .toolName(toolName)
                        .summary(result.getSummary())
                        .structuredData(SpecialistResultSupport.safeToolData(result))
                        .status(SpecialistResultStatus.SUCCESS)
                        .riskLevel(SpecialistRiskLevel.LOW)
                        .build();
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

    private static String intentForTool(String toolName) {
        return switch (toolName) {
            case "generate_resume_from_jd" -> "RESUME_GENERATE";
            case "search_knowledge_base" -> "RESUME_KB_SEARCH";
            default -> "RESUME_READ";
        };
    }

    public static boolean shouldGenerateResumeFromJd(String message) {
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
