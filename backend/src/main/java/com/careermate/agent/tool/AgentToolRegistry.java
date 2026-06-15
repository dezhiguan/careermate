package com.careermate.agent.tool;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> toolsByName = new LinkedHashMap<>();

    public AgentToolRegistry(List<AgentTool> tools) {
        for (AgentTool tool : tools) {
            String name = tool.name();
            if (toolsByName.containsKey(name)) {
                throw new IllegalStateException("Duplicate agent tool name: " + name);
            }
            toolsByName.put(name, tool);
        }
    }

    public Optional<AgentTool> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(toolsByName.get(name));
    }

    public List<AgentToolDefinition> listDefinitions() {
        return toolsByName.values().stream()
                .map(AgentTool::definition)
                .toList();
    }

    public Set<String> knownToolNames() {
        return Collections.unmodifiableSet(toolsByName.keySet());
    }

    public Optional<AgentToolDefinition> definitionOf(String name) {
        return findByName(name).map(AgentTool::definition);
    }
}
