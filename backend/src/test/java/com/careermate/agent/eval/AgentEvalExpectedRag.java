package com.careermate.agent.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentEvalExpectedRag(
        String tool,
        String scene,
        Integer topK,
        Integer minChunks
) {
}
