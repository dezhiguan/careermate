package com.careermate.workspace.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record MessageVO(
        Long id,
        Integer sequenceNo,
        String role,
        String content,
        String messageType,
        Map<String, Object> metadata,
        OffsetDateTime createdAt
) {
}
