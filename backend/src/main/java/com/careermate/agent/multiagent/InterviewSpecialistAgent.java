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
public class InterviewSpecialistAgent {

    private final AgentToolExecutionService toolExecutionService;

    public SpecialistResult process(AgentToolContext context, String userMessage) {
        try {
            AgentToolResult result = toolExecutionService.execute(context, "create_interview_session");
            if (result.isSuccess()) {
                return SpecialistResult.withTool(AgentDomain.INTERVIEW, "create_interview_session", result.getSummary());
            }
            return SpecialistResult.noTool(AgentDomain.INTERVIEW);
        } catch (Exception e) {
            log.warn("InterviewSpecialistAgent failed: {}", e.getMessage());
            return SpecialistResult.failed(AgentDomain.INTERVIEW, e.getMessage());
        }
    }
}
