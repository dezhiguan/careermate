package com.careermate.agent.context;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CareerProfileContextResult {

    private final boolean available;
    private final String contextText;
    private final String targetRole;
    private final int skillCount;

    public static CareerProfileContextResult empty() {
        return CareerProfileContextResult.builder()
                .available(false)
                .contextText("")
                .targetRole(null)
                .skillCount(0)
                .build();
    }
}
