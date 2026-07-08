package com.careermate.agent.eval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EvalGateTest {

    @Test
    void passesWhenWithinDropTolerance() {
        // 基线 0.8，跌到 0.75（跌 6.25% < 10%）→ 通过
        assertThat(EvalGate.passes(0.8, 0.75, 0.1)).isTrue();
    }

    @Test
    void blocksWhenDropExceedsTolerance() {
        // 基线 0.8，跌到 0.68（跌 15% > 10%）→ 阻塞
        assertThat(EvalGate.passes(0.8, 0.68, 0.1)).isFalse();
    }

    @Test
    void noBaseline_neverBlocks() {
        assertThat(EvalGate.passes(0.0, 0.1, 0.1)).isTrue();
    }

    @Test
    void improvement_passes() {
        assertThat(EvalGate.passes(0.8, 0.95, 0.1)).isTrue();
    }

    @Test
    void dropRatio_computed() {
        assertThat(EvalGate.dropRatio(0.8, 0.68)).isCloseTo(0.15, within(0.001));
        assertThat(EvalGate.dropRatio(0.8, 0.9)).isEqualTo(0.0);
        assertThat(EvalGate.dropRatio(0.0, 0.5)).isEqualTo(0.0);
    }
}
