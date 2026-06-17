package com.careermate.agent.context;

import com.careermate.agent.memory.AgentMemoryContext;
import com.careermate.agent.memory.AgentMemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
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
        try {
            AgentMemoryContext memory = agentMemoryService.loadMemoryContext(userId, sessionId);
            if (memory == null || !memory.isAvailable()) {
                return CareerProfileContextResult.empty();
            }
            return CareerProfileContextResult.builder()
                    .available(true)
                    .failed(false)
                    .errorCode(null)
                    .contextText(memory.getContextText())
                    .targetRole(memory.getTargetRole())
                    .skillCount(memory.getSkillCount())
                    .weaknessCount(memory.getWeaknessCount())
                    .hasSessionSummary(memory.isHasSessionSummary())
                    .build();
        } catch (Exception e) {
            log.warn("Failed to load memory context: userId={}, sessionId={}", userId, sessionId, e);
            return CareerProfileContextResult.failed();
        }
    }
}
