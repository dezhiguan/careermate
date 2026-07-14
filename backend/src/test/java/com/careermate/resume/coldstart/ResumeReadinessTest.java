package com.careermate.resume.coldstart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.careermate.common.exception.BizException;
import org.junit.jupiter.api.Test;

class ResumeReadinessTest {

    @Test
    void skeletonWithGuideMarkerIsNotFilled() {
        String skeleton = "## 工作经历\n> " + ResumeReadiness.GUIDE_MARKER + "公司 — 职位";
        assertThat(ResumeReadiness.isFilled(skeleton)).isFalse();
        assertThat(ResumeReadiness.resolveAfterEdit(skeleton)).isEqualTo(ResumeReadiness.DRAFT_SKELETON);
    }

    @Test
    void contentWithoutMarkerIsFilled() {
        String filled = "## 工作经历\n腾讯 — 高级工程师（2021-至今）";
        assertThat(ResumeReadiness.isFilled(filled)).isTrue();
        assertThat(ResumeReadiness.resolveAfterEdit(filled)).isEqualTo(ResumeReadiness.READY);
    }

    @Test
    void blankContentIsNotFilled() {
        assertThat(ResumeReadiness.isFilled("   ")).isFalse();
        assertThat(ResumeReadiness.isFilled(null)).isFalse();
    }

    @Test
    void ensureExportableBlocksDraftSkeletonWithFriendlyMessage() {
        assertThatThrownBy(() -> ResumeReadiness.ensureExportable(ResumeReadiness.DRAFT_SKELETON))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("填写引导");
    }

    @Test
    void ensureExportableAllowsReady() {
        assertThatCode(() -> ResumeReadiness.ensureExportable(ResumeReadiness.READY))
                .doesNotThrowAnyException();
    }

    @Test
    void isDraftSkeletonMatchesConstant() {
        assertThat(ResumeReadiness.isDraftSkeleton(ResumeReadiness.DRAFT_SKELETON)).isTrue();
        assertThat(ResumeReadiness.isDraftSkeleton(ResumeReadiness.READY)).isFalse();
        assertThat(ResumeReadiness.isDraftSkeleton(null)).isFalse();
    }
}
