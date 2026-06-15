package com.careermate.artifact.dto;

import java.util.Map;

public record CreateAgentArtifactCommand(
        Long userId,
        String sessionId,
        String artifactType,
        String title,
        String summary,
        String refType,
        String refId,
        Map<String, Object> metadata
) {
}
