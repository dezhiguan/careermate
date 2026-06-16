package com.careermate.agent.context;

import com.careermate.agent.memory.AgentMemoryContext;
import com.careermate.agent.memory.AgentMemoryService;
import org.springframework.stereotype.Component;

@Component
public class CareerProfileContextProvider {

    private final AgentMemoryService agentMemoryService;

    public CareerProfileContextProvider(AgentMemoryService agentMemoryService) {
        this.agentMemoryService = agentMemoryService;
    }

    public CareerProfileContextResult load(Long userId) {
        return load(userId, null);
    }

    public CareerProfileContextResult load(Long userId, String sessionId) {
        AgentMemoryContext memory = agentMemoryService.loadMemoryContext(userId, sessionId);
        if (memory == null || !memory.isAvailable()) {
            return CareerProfileContextResult.empty();
        }
        return CareerProfileContextResult.builder()
                .available(true)
                .contextText(memory.getContextText())
                .targetRole(memory.getTargetRole())
                .skillCount(memory.getSkillCount())
                .weaknessCount(memory.getWeaknessCount())
                .hasSessionSummary(memory.isHasSessionSummary())
                .build();
    }
}
