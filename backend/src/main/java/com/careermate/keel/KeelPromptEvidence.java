package com.careermate.keel;

import com.careermate.llm.dto.ChatMessage;
import java.util.*;

/** Bounded template provenance, shared across the native asynchronous request boundary. */
public final class KeelPromptEvidence {
    public record Version(String name, int version) {}
    private static final LinkedHashMap<String, Version> templates = new LinkedHashMap<>();
    private KeelPromptEvidence() {}

    public static synchronized void remember(String content, String name, int version) {
        if (content == null || content.isBlank()) return;
        templates.put(content, new Version(name, version));
        while (templates.size() > 64) templates.remove(templates.keySet().iterator().next());
    }

    public static synchronized Version find(List<ChatMessage> messages) {
        for (var entry : templates.entrySet()) {
            if (messages.stream().anyMatch(message -> "system".equals(message.getRole())
                    && message.getContent() != null && message.getContent().contains(entry.getKey()))) return entry.getValue();
        }
        return null;
    }
}
