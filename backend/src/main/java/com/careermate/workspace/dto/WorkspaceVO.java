package com.careermate.workspace.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record WorkspaceVO(
        String sessionId,
        String workspaceType,
        String title,
        String jdId,
        Map<String, Object> jdSnapshot,
        OffsetDateTime createdAt,
        OffsetDateTime lastActiveAt,
        List<ResumeVersionBriefVO> resumeVersions,
        String goalText,
        Map<String, Object> workspaceMetadata,
        String contextSummary,
        List<String> contextChips
) {
    public record ResumeVersionBriefVO(
            String versionId,
            String versionName,
            OffsetDateTime createdAt
    ) {
    }
}
