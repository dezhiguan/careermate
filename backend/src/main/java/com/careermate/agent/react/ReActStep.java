package com.careermate.agent.react;

public record ReActStep(
    int round,
    String thought,
    String action,       // 工具名 或 "final_answer"
    String observation   // 工具执行结果；final_answer 时为 null
) {}
