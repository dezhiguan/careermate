package com.careermate.agent.tool;

public interface AgentTool {

    String name();

    String description();

    boolean supports(AgentToolContext context);

    AgentToolResult execute(AgentToolContext context);
}
