package com.careermate.resume.version.dto;

import java.time.OffsetDateTime;

public record ResumeVersionListItemVO(
        String versionId,
        String versionName,
        String targetJdLabel,
        OffsetDateTime createdAt
) {
}
