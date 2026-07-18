package com.careermate.agent.multiagent;

import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.AgentToolExecutionService;
import com.careermate.agent.tool.AgentToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeSpecialistAgent implements SpecialistAgent {

    private static final SpecialistAgentSpec SPEC = new SpecialistAgentSpec(
            AgentDomain.RESUME,
            "ResumeSpecialistAgent",
            "负责读取默认简历、按 JD 生成或优化简历、在已有版本上做微调、检索知识库建议；不编造未发生的经历。",
            List.of("get_default_resume", "generate_resume_from_jd", "modify_resume", "search_knowledge_base"),
            SpecialistAgentSpec.INPUT_SCHEMA_V1,
            SpecialistAgentSpec.OUTPUT_SCHEMA_V1
    );

    private final AgentToolExecutionService toolExecutionService;
    private final com.careermate.resume.version.service.ResumeVersionService resumeVersionService;

    @Override
    public SpecialistAgentSpec spec() {
        return SPEC;
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
            String toolName = pickTool(context, userMessage);
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

    private String pickTool(AgentToolContext context, String message) {
        if (message != null) {
            if (shouldGenerateResumeFromJd(message)) {
                // 已有简历版本 + 微调意图(非重做整份) → 走 modify_resume 增量修改
                if (isModifyIntent(message) && !isRegenerateIntent(message) && hasResumeVersion(context)) {
                    return "modify_resume";
                }
                return "generate_resume_from_jd";
            }
            if (message.contains("搜索") || message.contains("推荐")
                    || message.contains("建议") || message.contains("知识库")) {
                return "search_knowledge_base";
            }
        }
        return "get_default_resume";
    }

    /** 微调意图（改动某处，非重做整份）。 */
    static boolean isModifyIntent(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return containsAny(lower, "改成", "换成", "调成", "改为", "加一段", "加上", "补一段", "补充",
                "删掉", "去掉", "换个说法", "换种说法", "调整", "微调", "改一下", "改下", "这里改", "这段改");
    }

    /** 明确要重做整份/重新生成。 */
    static boolean isRegenerateIntent(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return containsAny(lower, "重新生成", "重做", "重写整份", "再生成一份", "换一份", "重新定制");
    }

    private boolean hasResumeVersion(AgentToolContext context) {
        try {
            return context != null && context.getSessionId() != null
                    && !resumeVersionService.listBySession(context.getUserId(), context.getSessionId()).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static String intentForTool(String toolName) {
        return switch (toolName) {
            case "generate_resume_from_jd" -> "RESUME_GENERATE";
            case "modify_resume" -> "RESUME_MODIFY";
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
