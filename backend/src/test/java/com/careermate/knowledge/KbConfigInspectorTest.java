package com.careermate.knowledge;

import com.careermate.ragforge.RagForgeProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 评审第九章 · 守卫 #1：知识库配置完整性自检。
 */
class KbConfigInspectorTest {

    private RagForgeProperties props() {
        RagForgeProperties p = new RagForgeProperties();
        p.setEnabled(true);
        return p;
    }

    @Test
    void allSixSlotsMissingWhenNothingConfigured() {
        KbConfigInspector inspector = new KbConfigInspector(props());

        List<KbConfigInspector.KbSlot> slots = inspector.inspect();
        assertEquals(6, slots.size());
        assertTrue(slots.stream().noneMatch(KbConfigInspector.KbSlot::configured));
        assertEquals(6, inspector.missingLabels().size());
        assertFalse(inspector.allConfigured());
    }

    @Test
    void allConfiguredWhenSixValidIds() {
        RagForgeProperties p = props();
        p.setJdKbId("599");
        p.setInterviewKbId("596");
        p.setMarketKbId("597");
        p.setCompanyKbId("595");
        p.setSkillKbId("598");
        p.setPersonalKbId("601");

        KbConfigInspector inspector = new KbConfigInspector(p);

        assertTrue(inspector.allConfigured());
        assertTrue(inspector.missingLabels().isEmpty());
        assertTrue(inspector.inspect().stream().allMatch(KbConfigInspector.KbSlot::configured));
    }

    @Test
    void partialConfigReportsOnlyMissingLabels() {
        RagForgeProperties p = props();
        p.setJdKbId("599");
        p.setInterviewKbId("596");
        // 薪资/公司/技能/个人 未配

        KbConfigInspector inspector = new KbConfigInspector(p);

        List<String> missing = inspector.missingLabels();
        assertEquals(List.of("薪资行情库", "目标公司库", "岗位技能画像库", "个人简历库"), missing);
        assertFalse(inspector.allConfigured());
    }

    @Test
    void blankAndNonNumericAndNonPositiveIdsCountAsMissing() {
        RagForgeProperties p = props();
        p.setJdKbId("   ");        // 空白
        p.setInterviewKbId("abc"); // 非数字
        p.setMarketKbId("0");      // 非正
        p.setCompanyKbId("-5");    // 负数
        p.setSkillKbId("598");     // 有效
        p.setPersonalKbId("601");  // 有效

        KbConfigInspector inspector = new KbConfigInspector(p);

        assertEquals(4, inspector.missingLabels().size());
        assertTrue(inspector.missingLabels().contains("岗位 JD 库"));
        assertTrue(inspector.missingLabels().contains("面试题库"));
        assertTrue(inspector.missingLabels().contains("薪资行情库"));
        assertTrue(inspector.missingLabels().contains("目标公司库"));
    }

    @Test
    void trimsWhitespaceAroundValidId() {
        RagForgeProperties p = props();
        p.setJdKbId("  599  ");

        KbConfigInspector.KbSlot jd = new KbConfigInspector(p).inspect().get(0);
        assertEquals("jdKbId", jd.key());
        assertTrue(jd.configured());
        assertEquals("599", jd.id());
    }

    @Test
    void startupLoggersDoNotThrow() {
        // 覆盖三条启动日志分支：未启用 / 全配置 / 有缺失
        RagForgeProperties disabled = new RagForgeProperties();
        disabled.setEnabled(false);
        new KbConfigInspector(disabled).logOnStartup();

        RagForgeProperties partial = props();
        partial.setJdKbId("599");
        new KbConfigInspector(partial).logOnStartup();

        RagForgeProperties full = props();
        full.setJdKbId("599");
        full.setInterviewKbId("596");
        full.setMarketKbId("597");
        full.setCompanyKbId("595");
        full.setSkillKbId("598");
        full.setPersonalKbId("601");
        new KbConfigInspector(full).logOnStartup();
    }
}
