package com.careermate.agent.context;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CareerProfileContextResult {

    private final boolean available;
    private final boolean failed;
    private final String errorCode;
    private final String contextText;
    private final String targetRole;
    private final int skillCount;
    private final int weaknessCount;
    private final boolean hasSessionSummary;

    public static CareerProfileContextResult empty() {
        return CareerProfileContextResult.builder()
                .available(false)
                .failed(false)
                .errorCode(null)
                .contextText("")
                .targetRole(null)
                .skillCount(0)
                .weaknessCount(0)
                .hasSessionSummary(false)
                .build();
    }

    public static CareerProfileContextResult failed() {
        return CareerProfileContextResult.builder()
                .available(false)
                .failed(true)
                .errorCode("MEMORY_LOAD_FAILED")
                .contextText("")
                .targetRole(null)
                .skillCount(0)
                .weaknessCount(0)
                .hasSessionSummary(false)
                .build();
    }
}
