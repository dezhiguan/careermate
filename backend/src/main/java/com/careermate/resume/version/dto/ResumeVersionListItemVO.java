package com.careermate.resume.version.dto;

import java.time.OffsetDateTime;

public record ResumeVersionListItemVO(
        String versionId,
        String versionName,
        String targetJdLabel,
        String targetCompany,
        Long targetJdId,
        String targetJdTitle,
        Integer versionSeq,
        OffsetDateTime createdAt
) {
}
