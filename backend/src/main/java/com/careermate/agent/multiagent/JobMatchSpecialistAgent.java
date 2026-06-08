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
public class JobMatchSpecialistAgent {

    private final AgentToolExecutionService toolExecutionService;

    public SpecialistResult process(AgentToolContext context, String userMessage) {
        try {
            boolean hasJd = userMessage != null && userMessage.length() > 100
                && (userMessage.contains("JD") || userMessage.contains("岗位")
                    || userMessage.contains("职位") || userMessage.contains("招聘"));
            String toolName = hasJd ? "create_job_match" : "get_latest_job_match";
            AgentToolResult result = toolExecutionService.execute(context, toolName);
            if (result.isSuccess()) {
                return SpecialistResult.withTool(AgentDomain.JOB_MATCH, toolName, result.getSummary());
            }
            return SpecialistResult.noTool(AgentDomain.JOB_MATCH);
        } catch (Exception e) {
            log.warn("JobMatchSpecialistAgent failed: {}", e.getMessage());
            return SpecialistResult.failed(AgentDomain.JOB_MATCH, e.getMessage());
        }
    }
}
