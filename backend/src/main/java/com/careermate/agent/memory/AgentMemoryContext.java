package com.careermate.agent.memory;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
@Builder
public class AgentMemoryContext {

    private final boolean available;
    private final String contextText;
    private final String targetRole;
    private final String targetCity;
    private final String targetSalaryRange;
    private final List<String> skillKeywords;
    private final List<String> weaknessKeywords;
    private final String interviewWeaknessSummary;
    private final String memorySummary;
    private final String conversationSummary;
    private final int skillCount;
    private final int weaknessCount;
    private final boolean hasSessionSummary;

    public static AgentMemoryContext empty() {
        return AgentMemoryContext.builder()
                .available(false)
                .contextText("")
                .skillKeywords(Collections.emptyList())
                .weaknessKeywords(Collections.emptyList())
                .skillCount(0)
                .weaknessCount(0)
                .hasSessionSummary(false)
                .build();
    }
}
