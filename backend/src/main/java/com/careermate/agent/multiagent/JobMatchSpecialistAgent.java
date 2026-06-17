package com.careermate.agent.multiagent;

import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.AgentToolExecutionService;
import com.careermate.agent.tool.AgentToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobMatchSpecialistAgent implements SpecialistAgent {

    private final AgentToolExecutionService toolExecutionService;

    @Override
    public AgentDomain domain() {
        return AgentDomain.JOB_MATCH;
    }

    @Override
    public String agentName() {
        return "JobMatchSpecialistAgent";
    }

    public SpecialistResult process(AgentToolContext context, String userMessage) {
        return process(context, userMessage, new AgentSupervisorRoute(
                java.util.List.of(AgentDomain.JOB_MATCH),
                AgentDomain.JOB_MATCH,
                1D,
                "LEGACY_SINGLE",
                false
        ));
    }

    @Override
    public SpecialistResult process(AgentToolContext context, String userMessage, AgentSupervisorRoute route) {
        try {
            boolean hasJd = userMessage != null && userMessage.length() > 100
                    && (userMessage.contains("JD") || userMessage.contains("岗位")
                    || userMessage.contains("职位") || userMessage.contains("招聘"));
            String toolName = hasJd ? "create_job_match" : "get_latest_job_match";
            AgentToolResult result = toolExecutionService.execute(context, toolName);
            if (result.isSuccess()) {
                return SpecialistResult.builder()
                        .domain(AgentDomain.JOB_MATCH)
                        .agentName(agentName())
                        .intent(hasJd ? "JOB_MATCH_CREATE" : "JOB_MATCH_READ")
                        .toolName(toolName)
                        .summary(result.getSummary())
                        .structuredData(SpecialistResultSupport.safeToolData(result))
                        .status(SpecialistResultStatus.SUCCESS)
                        .riskLevel(SpecialistRiskLevel.LOW)
                        .build();
            }
            return SpecialistResult.noTool(AgentDomain.JOB_MATCH);
        } catch (Exception e) {
            log.warn("JobMatchSpecialistAgent failed: {}", e.getMessage());
            return SpecialistResult.failed(AgentDomain.JOB_MATCH, e.getMessage());
        }
    }
}
