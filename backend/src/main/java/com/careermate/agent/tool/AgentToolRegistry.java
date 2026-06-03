package com.careermate.agent.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> toolsByName = new LinkedHashMap<>();

    public AgentToolRegistry(List<AgentTool> tools) {
        for (AgentTool tool : tools) {
            toolsByName.put(tool.name(), tool);
        }
    }

    public Optional<AgentTool> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(toolsByName.get(name));
    }
}
