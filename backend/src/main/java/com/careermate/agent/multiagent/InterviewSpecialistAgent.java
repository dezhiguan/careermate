package com.careermate.agent.multiagent;

import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.AgentToolExecutionService;
import com.careermate.agent.tool.AgentToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewSpecialistAgent implements SpecialistAgent {

    private final AgentToolExecutionService toolExecutionService;

    @Override
    public AgentDomain domain() {
        return AgentDomain.INTERVIEW;
    }

    @Override
    public String agentName() {
        return "InterviewSpecialistAgent";
    }

    public SpecialistResult process(AgentToolContext context, String userMessage) {
        return process(context, userMessage, new AgentSupervisorRoute(
                java.util.List.of(AgentDomain.INTERVIEW),
                AgentDomain.INTERVIEW,
                1D,
                "LEGACY_SINGLE",
                false
        ));
    }

    @Override
    public SpecialistResult process(AgentToolContext context, String userMessage, AgentSupervisorRoute route) {
        try {
            if (isKnowledgeOnlyQuestion(userMessage)) {
                return SpecialistResult.builder()
                        .domain(AgentDomain.INTERVIEW)
                        .agentName(agentName())
                        .intent("INTERVIEW_KNOWLEDGE_ONLY")
                        .status(SpecialistResultStatus.NO_ACTION)
                        .summary("面试知识型问题交由知识检索工具处理。")
                        .build();
            }
            AgentToolResult result = toolExecutionService.execute(context, "create_interview_session");
            if (result.isSuccess()) {
                return SpecialistResult.builder()
                        .domain(AgentDomain.INTERVIEW)
                        .agentName(agentName())
                        .intent("INTERVIEW_SESSION")
                        .toolName("create_interview_session")
                        .summary(result.getSummary())
                        .structuredData(SpecialistResultSupport.safeToolData(result))
                        .status(SpecialistResultStatus.SUCCESS)
                        .riskLevel(SpecialistRiskLevel.LOW)
                        .build();
            }
            return SpecialistResult.noTool(AgentDomain.INTERVIEW);
        } catch (Exception e) {
            log.warn("InterviewSpecialistAgent failed: {}", e.getMessage());
            return SpecialistResult.failed(AgentDomain.INTERVIEW, e.getMessage());
        }
    }

    private static boolean isKnowledgeOnlyQuestion(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return containsAny(lower, "面试题", "面试问答", "面试参考", "redis", "jvm", "spring", "kafka")
                && !containsAny(lower, "准备面试", "模拟面试", "开始面试", "创建面试", "面试练习");
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
