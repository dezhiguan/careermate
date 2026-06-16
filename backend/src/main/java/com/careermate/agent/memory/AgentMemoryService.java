package com.careermate.agent.memory;

import java.util.List;

public interface AgentMemoryService {

    AgentMemoryContext loadMemoryContext(Long userId, String sessionId);

    void refreshConversationSummary(Long userId, String sessionId);

    void rememberInterviewWeakness(
            Long userId,
            int score,
            String questionType,
            String questionText,
            List<String> referencePoints,
            List<String> improvements
    );

    void mergeCareerSignals(
            Long userId,
            List<String> skillKeywords,
            List<String> weaknessKeywords,
            String weaknessSummarySnippet
    );
}
