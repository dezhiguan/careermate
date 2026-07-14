package com.careermate.resume.coldstart;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class DefaultResumeSkeletonBuilderTest {

    private final DefaultResumeSkeletonBuilder builder = new DefaultResumeSkeletonBuilder();

    private static final List<String> P2_PLACEHOLDER_MARKERS = List.of(
            "暂无", "待补充", "示例", "公司A", "项目A", "某某公司", "XXX", "xxx");

    @Test
    void l3DefaultSkeletonHasAllSectionsAndGuideMarkers() {
        ColdStartResume r = builder.buildDefaultSkeleton();
        assertThat(r.origin()).isEqualTo(ResumeOrigin.COLD_START);
        assertThat(r.readiness()).isEqualTo(ResumeReadiness.DRAFT_SKELETON);
        assertThat(r.isDraftSkeleton()).isTrue();
        assertThat(r.content())
                .contains("个人优势", "专业技能", "工作经历", "项目经历", "教育经历")
                .contains(ResumeReadiness.GUIDE_MARKER);
        assertThat(r.sourceSignals()).contains("default_skeleton");
    }

    @Test
    void defaultSkeletonContainsNoFabricatedFactsNorP2Placeholders() {
        String content = builder.buildDefaultSkeleton().content();
        // 不得预置任何虚构机构/院校/证书
        assertThat(content).doesNotContain("有限公司", "大学", "学院", "证书");
        // 不得触发 P2 占位词
        for (String marker : P2_PLACEHOLDER_MARKERS) {
            assertThat(content).doesNotContain(marker);
        }
    }

    @Test
    void l2PrefillsTargetRoleAndSkills() {
        ColdStartResume r = builder.buildFromProfile("高级 Java 工程师", List.of("Spring Boot", "Kafka"));
        assertThat(r.title()).contains("高级 Java 工程师");
        assertThat(r.content()).contains("高级 Java 工程师");
        // 技能被真实预填，且该行不带引导标记
        assertThat(r.content()).contains("Spring Boot · Kafka");
        // 其余章节仍是引导占位 → 仍为草稿骨架
        assertThat(r.readiness()).isEqualTo(ResumeReadiness.DRAFT_SKELETON);
        assertThat(r.sourceSignals()).contains("career_profile");
    }

    @Test
    void l2WithNoSignalsFallsBackToGuidedSkeleton() {
        ColdStartResume r = builder.buildFromProfile(null, List.of());
        assertThat(r.title()).isEqualTo("我的简历（待完善）");
        assertThat(r.content()).contains(ResumeReadiness.GUIDE_MARKER);
    }

    @Test
    void l2FiltersBlankSkills() {
        ColdStartResume r = builder.buildFromProfile("后端", List.of(" ", "Redis", ""));
        assertThat(r.content()).contains("Redis");
        assertThat(r.content()).doesNotContain("·  ·");
    }
}
