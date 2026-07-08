package com.careermate.agent.eval;

import java.util.List;

/**
 * A5：评测场景。
 *
 * @param id                 场景 id
 * @param question           输入问题
 * @param criteria           评分标准（知识库锚定）
 * @param scene              KB 场景（resume/salary/interview/company）
 * @param reflectionRequired 是否"需反思才能答对"专项
 */
public record EvalScenario(
        String id,
        String question,
        List<String> criteria,
        String scene,
        boolean reflectionRequired
) {
}
