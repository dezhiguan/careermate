package com.careermate.resume.version.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ResumeVersionListItemVO(
        String versionId,
        String versionName,
        String targetJdLabel,
        String targetCompany,
        Long targetJdId,
        String targetJdTitle,
        Integer versionSeq,
        String changeSummary,
        BigDecimal aiScore,
        boolean exported,
        int reusedCount,
        OffsetDateTime createdAt
) {
}
