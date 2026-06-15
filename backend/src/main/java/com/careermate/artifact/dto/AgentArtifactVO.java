package com.careermate.artifact.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record AgentArtifactVO(
        String artifactId,
        String artifactType,
        String title,
        String summary,
        String refType,
        String refId,
        String sessionId,
        Map<String, Object> metadata,
        OffsetDateTime createdAt,
        String actionLabel,
        String actionRoute
) {
}
