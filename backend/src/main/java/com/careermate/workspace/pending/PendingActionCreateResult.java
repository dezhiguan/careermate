package com.careermate.workspace.pending;

import java.time.OffsetDateTime;
import java.util.Map;

public record PendingActionCreateResult(
        String actionId,
        Map<String, Object> confirmCard,
        OffsetDateTime expiresAt
) {
}
