package com.careermate.agent.reflect;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReflectiveAgentEngineTest {

    private final AgentPlanner planner = mock(AgentPlanner.class);
    private final AgentReflector reflector = mock(AgentReflector.class);
    private final AgentRepairer repairer = mock(AgentRepairer.class);
    private final ReflectionProperties props = new ReflectionProperties();
    private final ReflectiveAgentEngine engine = new ReflectiveAgentEngine(planner, reflector, repairer, props);

    private AgentPlan plan(int round) {
        return new AgentPlan((long) round, "run-1", round, List.of("g"), List.of("s"), List.of("c"), null);
    }

    private Reflection satisfied() {
        return new Reflection(true, 0.9, List.of(), List.of(), "ACCEPT");
    }

    private Reflection revise(String sug) {
        return new Reflection(false, 0.4, List.of("gap"), List.of(sug), "REVISE");
    }

    @Test
    void stopsAtConsensusFirstRound() {
        when(planner.plan(anyString(), anyString())).thenReturn(plan(0));
        when(reflector.review(any(), any())).thenReturn(satisfied());

        ReflectiveRunResult r = engine.run("run-1", "任务", p -> "草稿");

        assertThat(r.status()).isEqualTo("CONSENSUS");
        assertThat(r.rounds()).isEqualTo(1);
        assertThat(r.reachedConsensus()).isTrue();
    }

    @Test
    void revisesThenConsensus() {
        when(planner.plan(anyString(), anyString())).thenReturn(plan(0));
        when(reflector.review(any(), any())).thenReturn(revise("补A"), satisfied());
        when(repairer.isStuck(any(), any())).thenReturn(false);
        when(repairer.revise(any(), any())).thenReturn(plan(1));

        ReflectiveRunResult r = engine.run("run-1", "任务", p -> "草稿");

        assertThat(r.status()).isEqualTo("CONSENSUS");
        assertThat(r.rounds()).isEqualTo(2);
    }

    @Test
    void hitsMaxRoundsWhenNeverSatisfied() {
        props.setMaxRounds(3);
        when(planner.plan(anyString(), anyString())).thenReturn(plan(0));
        // 每轮不同建议，避免触发 stuck
        AtomicInteger i = new AtomicInteger();
        when(reflector.review(any(), any())).thenAnswer(inv -> revise("补" + i.incrementAndGet()));
        when(repairer.isStuck(any(), any())).thenReturn(false);
        when(repairer.revise(any(), any())).thenReturn(plan(1));

        ReflectiveRunResult r = engine.run("run-1", "任务", p -> "草稿");

        assertThat(r.status()).isEqualTo("MAX_ROUNDS");
        assertThat(r.rounds()).isEqualTo(3);
    }

    @Test
    void earlyStopWhenStuck() {
        when(planner.plan(anyString(), anyString())).thenReturn(plan(0));
        when(reflector.review(any(), any())).thenReturn(revise("同一建议"), revise("同一建议"));
        when(repairer.isStuck(any(), any())).thenReturn(true);
        when(repairer.revise(any(), any())).thenReturn(plan(1));

        ReflectiveRunResult r = engine.run("run-1", "任务", p -> "草稿");

        assertThat(r.status()).isEqualTo("STUCK_EARLY_STOP");
    }

    @Test
    void failVerdictStopsImmediately() {
        when(planner.plan(anyString(), anyString())).thenReturn(plan(0));
        when(reflector.review(any(), any())).thenReturn(new Reflection(false, 0.1, List.of("无解"), List.of(), "FAIL"));

        ReflectiveRunResult r = engine.run("run-1", "任务", p -> "草稿");

        assertThat(r.status()).isEqualTo("FAIL");
    }

    @Test
    void actThrows_returnsFailGracefully() {
        when(planner.plan(anyString(), anyString())).thenReturn(plan(0));

        ReflectiveRunResult r = engine.run("run-1", "任务", p -> {
            throw new RuntimeException("act down");
        });

        assertThat(r.status()).isEqualTo("FAIL");
    }
}
