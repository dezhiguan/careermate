package com.careermate.agent.context;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ConversationContextResult {

    private final boolean available;
    private final String contextText;
    private final int messageCount;
    private final int charCount;
    private final boolean loadFailed;

    public static ConversationContextResult empty() {
        return ConversationContextResult.builder()
                .available(false)
                .contextText("")
                .messageCount(0)
                .charCount(0)
                .loadFailed(false)
                .build();
    }

    public static ConversationContextResult failed() {
        return ConversationContextResult.builder()
                .available(false)
                .contextText("")
                .messageCount(0)
                .charCount(0)
                .loadFailed(true)
                .build();
    }
}
