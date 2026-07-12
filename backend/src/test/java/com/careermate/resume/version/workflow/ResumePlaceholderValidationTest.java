package com.careermate.resume.version.workflow;

import com.careermate.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * #5.3 回归：占位词校验不再误杀含“示例/xxx”的合法简历，但仍拦截明确模板占位。
 */
class ResumePlaceholderValidationTest {

    @Test
    void acceptsLegitContentWithShiliAndXxx() {
        String legit = "# 张三\n## 项目经历\n- 主导支付重构，产出接口文档与代码示例，覆盖率 90%\n"
                + "## 联系方式\n邮箱 zhangsan@xxx.com";
        assertDoesNotThrow(() -> GenerateResumeWorkflowRunner.validateMarkdownQuality(legit));
    }

    @Test
    void stillRejectsTemplatePlaceholder() {
        String template = "# 张三\n## 工作经历\n- 在 公司A 负责 项目A 的开发";
        assertThrows(BizException.class,
                () -> GenerateResumeWorkflowRunner.validateMarkdownQuality(template));
    }

    @Test
    void rejectsEmptyOutput() {
        assertThrows(BizException.class,
                () -> GenerateResumeWorkflowRunner.validateMarkdownQuality("   "));
    }
}
