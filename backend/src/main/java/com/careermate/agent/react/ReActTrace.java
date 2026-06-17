package com.careermate.agent.react;

import java.util.List;

public record ReActTrace(
    List<ReActStep> steps,
    boolean reachedFinalAnswer,
    int rounds
) {
    public boolean hasSteps() {
        return steps != null && !steps.isEmpty();
    }

    /** 把工具执行摘要拼成可注入 system prompt 的文本（不包含 Thought） */
    public String toContextText() {
        if (!hasSteps()) return "";
        StringBuilder sb = new StringBuilder("【工具执行摘要】\n");
        for (ReActStep s : steps) {
            sb.append("Round ").append(s.round()).append(":\n");
            if (s.action() != null && !s.action().isBlank()) {
                sb.append("  Action: ").append(s.action()).append("\n");
            }
            if (s.observation() != null) {
                String obs = s.observation().length() > 300
                    ? s.observation().substring(0, 300) + "..."
                    : s.observation();
                sb.append("  Observation: ").append(obs).append("\n");
            }
        }
        return sb.toString();
    }
}
