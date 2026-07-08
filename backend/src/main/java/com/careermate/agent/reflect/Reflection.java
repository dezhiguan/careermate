package com.careermate.agent.reflect;

import java.util.List;

/**
 * A3：reflector 判定结果。
 *
 * @param satisfied   是否达成成功标准
 * @param confidence  置信度 0-1
 * @param gaps        差距
 * @param suggestions 修订建议
 * @param verdict     REVISE / ACCEPT / FAIL
 */
public record Reflection(
        boolean satisfied,
        double confidence,
        List<String> gaps,
        List<String> suggestions,
        String verdict
) {
    public static Reflection accept() {
        return new Reflection(true, 1.0, List.of(), List.of(), "ACCEPT");
    }
}
