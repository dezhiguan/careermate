package com.careermate.agent.reflect;

import com.careermate.llm.LlmClient;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeepPathReflectionRunnerTest {

    private final ReflectiveAgentEngine engine = mock(ReflectiveAgentEngine.class);
    private final LlmClient llm = mock(LlmClient.class);
    private final ReflectionProperties props = new ReflectionProperties();
    private final DeepPathReflectionRunner runner = new DeepPathReflectionRunner(engine, llm, props);

    private AgentPlan planWithCriteria(List<String> criteria) {
        return new AgentPlan(1L, "run", 1, List.of("g"), List.of("s"), criteria, null);
    }

    @Test
    void disabled_returnsEmptyAndSkipsEngine() {
        props.setEnabled(false);
        assertThat(runner.isEnabled()).isFalse();
        assertThat(runner.refine("sys", "hi")).isEmpty();
    }

    @Test
    void enabled_injectsCriteriaAsRequirement() {
        props.setEnabled(true);
        when(engine.run(anyString(), anyString(), any())).thenReturn(
                new ReflectiveRunResult(planWithCriteria(List.of("覆盖JD库≥5项", "薪资有分位数支撑")),
                        Reflection.accept(), "CONSENSUS", 1));

        String req = runner.refine("sys", "针对字节改简历");

        assertThat(req).contains("最终作答要求");
        assertThat(req).contains("覆盖JD库≥5项");
        assertThat(req).contains("薪资有分位数支撑");
    }

    @Test
    void nonConsensus_alsoInjectsGaps() {
        String req = DeepPathReflectionRunner.buildRequirement(new ReflectiveRunResult(
                planWithCriteria(List.of("c1")),
                new Reflection(false, 0.4, List.of("缺Spring Cloud"), List.of("补检索"), "MAX_ROUNDS_VERDICT"),
                "MAX_ROUNDS", 3));
        assertThat(req).contains("缺Spring Cloud");
    }

    @Test
    void emptyCriteriaAndConsensus_returnsEmpty() {
        String req = DeepPathReflectionRunner.buildRequirement(new ReflectiveRunResult(
                planWithCriteria(List.of()), Reflection.accept(), "CONSENSUS", 1));
        assertThat(req).isEmpty();
    }

    @Test
    void engineThrows_returnsEmptyGracefully() {
        props.setEnabled(true);
        when(engine.run(anyString(), anyString(), any())).thenThrow(new RuntimeException("boom"));
        assertThat(runner.refine("sys", "hi")).isEmpty();
    }
}
