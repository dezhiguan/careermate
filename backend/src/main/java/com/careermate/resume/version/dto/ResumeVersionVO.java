package com.careermate.resume.version.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ResumeVersionVO(
        String versionId,
        String versionName,
        String sessionId,
        String targetJdId,
        String targetJdLabel,
        String contentMarkdown,
        List<Map<String, Object>> optimizationNotes,
        BigDecimal aiScore,
        OffsetDateTime createdAt
) {
}
