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

    /** 把推理链拼成可注入 system prompt 的文本 */
    public String toContextText() {
        if (!hasSteps()) return "";
        StringBuilder sb = new StringBuilder("【ReAct 推理链】\n");
        for (ReActStep s : steps) {
            sb.append("Round ").append(s.round()).append(":\n");
            sb.append("  Thought: ").append(s.thought()).append("\n");
            sb.append("  Action: ").append(s.action()).append("\n");
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
