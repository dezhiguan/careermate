package com.careermate.agent.multiagent;

import com.careermate.agent.tool.AgentToolContext;

public interface SpecialistAgent {

    AgentDomain domain();

    String agentName();

    default boolean supports(AgentSupervisorRoute route) {
        return route != null && route.selectedDomains() != null && route.selectedDomains().contains(domain());
    }

    SpecialistResult process(AgentToolContext context, String userMessage, AgentSupervisorRoute route);
}
