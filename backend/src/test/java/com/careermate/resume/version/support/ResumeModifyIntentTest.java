package com.careermate.resume.version.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResumeModifyIntentTest {

    @Test
    void modifyPhrasesAreDetected() {
        assertTrue(ResumeModifyIntent.isModifyOnExistingIntent("帮我把这份简历的期望薪资改成30-45k，别的都别动"));
        assertTrue(ResumeModifyIntent.isModifyOnExistingIntent("加一段开源贡献经历"));
        assertTrue(ResumeModifyIntent.isModifyOnExistingIntent("删掉前端那条"));
        assertTrue(ResumeModifyIntent.isModifyOnExistingIntent("这段换个说法"));
    }

    @Test
    void regenerateIntentOverridesModify() {
        // 含「重做/重新生成」等整份重做词时，即便也出现「改」字，也不算微调
        assertFalse(ResumeModifyIntent.isModifyOnExistingIntent("重新生成一份，把薪资改成30k"));
        assertFalse(ResumeModifyIntent.isModifyOnExistingIntent("重做整份简历"));
        assertFalse(ResumeModifyIntent.isModifyOnExistingIntent("换一份简历"));
    }

    @Test
    void nonModifyMessagesAreIgnored() {
        assertFalse(ResumeModifyIntent.isModifyOnExistingIntent("这份简历怎么样？"));
        assertFalse(ResumeModifyIntent.isModifyOnExistingIntent("帮我按这个 JD 生成一份简历"));
        assertFalse(ResumeModifyIntent.isModifyOnExistingIntent(null));
        assertFalse(ResumeModifyIntent.isModifyOnExistingIntent("   "));
    }
}
