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
        Set.of("get_default_resume", "search_knowledge_base");

    private final AgentToolExecutionService toolExecutionService;

    public SpecialistResult process(AgentToolContext context, String userMessage) {
        try {
            String toolName = pickTool(userMessage);
            AgentToolResult result = toolExecutionService.execute(context, toolName);
            if (result.isSuccess()) {
                return SpecialistResult.withTool(AgentDomain.RESUME, toolName, result.getSummary());
            }
            return SpecialistResult.noTool(AgentDomain.RESUME);
        } catch (Exception e) {
            log.warn("ResumeSpecialistAgent failed: {}", e.getMessage());
            return SpecialistResult.failed(AgentDomain.RESUME, e.getMessage());
        }
    }

    private String pickTool(String message) {
        if (message != null && (message.contains("搜索") || message.contains("推荐")
                || message.contains("建议") || message.contains("知识库"))) {
            return "search_knowledge_base";
        }
        return "get_default_resume";
    }
}
