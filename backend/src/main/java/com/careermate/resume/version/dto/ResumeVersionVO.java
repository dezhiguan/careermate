package com.careermate.resume.version.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ResumeVersionVO(
        String versionId,
        String versionName,
        String sessionId,
        Long targetJdId,
        String targetJdLabel,
        String targetCompany,
        String targetJdTitle,
        Integer versionSeq,
        String changeSummary,
        String contentMarkdown,
        List<Map<String, Object>> optimizationNotes,
        BigDecimal aiScore,
        OffsetDateTime createdAt,
        /** P4 版本来源：GENERATED / MANUAL_EDIT。 */
        String origin,
        /** P2/P4 事实校验结果 JSON（含疑似无出处项），供前端标黄提示；null 表示未校验或全通过。 */
        String factCheck
) {
}
