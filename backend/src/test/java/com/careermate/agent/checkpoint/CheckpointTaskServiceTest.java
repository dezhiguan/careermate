package com.careermate.agent.checkpoint;

import com.careermate.mapper.AgentRunMapper;
import com.careermate.model.entity.AgentRunEntity;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CheckpointTaskServiceTest {

    private final Map<String, AgentState> mem = new HashMap<>();
    private final CheckpointStore store = mock(CheckpointStore.class);
    private final AgentRunMapper runMapper = mock(AgentRunMapper.class);
    private final CheckpointProperties props = new CheckpointProperties();

    private CheckpointTaskService newService(Long ownerUserId) {
        when(store.save(anyString(), anyString(), any(), anyBoolean(), any())).thenAnswer(inv -> {
            mem.put(inv.getArgument(0), inv.getArgument(2));
            return true;
        });
        when(store.loadLatest(anyString())).thenAnswer(inv -> Optional.ofNullable(mem.get((String) inv.getArgument(0))));
        when(runMapper.selectById(any())).thenAnswer(inv -> {
            if (ownerUserId == null) return null;
            AgentRunEntity e = new AgentRunEntity();
            e.setUserId(ownerUserId);
            return e;
        });
        props.setEnabled(true);
        CheckpointedAgentEngine engine = new CheckpointedAgentEngine(store, runMapper);
        return new CheckpointTaskService(engine, store, runMapper, props);
    }

    @Test
    void start_runsToHitlPauseWithContext() {
        AgentState s = newService(1L).startRun(1L, "字节 Java 后端准备");
        assertThat(s.paused()).isTrue();
        assertThat(s.currentStep()).isEqualTo("awaiting_direction");
        assertThat(s.pendingAction()).contains("选择");
        assertThat(s.data()).containsKey("topic").containsKey("analysis");
        assertThat(String.valueOf(s.data().get("topic"))).contains("字节");
    }

    @Test
    void resume_injectsDecisionAndFinishes() {
        CheckpointTaskService svc = newService(1L);
        AgentState paused = svc.startRun(1L, "简历深化");
        AgentState done = svc.resumeRun(1L, paused.runId(), "项目包装");
        assertThat(done).isNotNull();
        assertThat(done.currentStep()).isEqualTo("done");
        assertThat(done.isTerminal()).isTrue();
        // 续跑保留了前序上下文，并带上用户决定
        assertThat(String.valueOf(done.data().get("result"))).contains("项目包装");
        assertThat(done.data()).containsKey("topic");
    }

    @Test
    void resume_rejectsWhenNotOwner() {
        CheckpointTaskService svc = newService(1L);
        AgentState paused = svc.startRun(1L, "x");
        // 换成引擎侧不属于当前用户
        when(runMapper.selectById(any())).thenAnswer(inv -> {
            AgentRunEntity e = new AgentRunEntity();
            e.setUserId(999L);
            return e;
        });
        assertThat(svc.resumeRun(1L, paused.runId(), "面试准备")).isNull();
    }

    @Test
    void fork_createsNewRunFromCheckpoint() {
        CheckpointTaskService svc = newService(1L);
        AgentState paused = svc.startRun(1L, "换方向重试");
        String forked = svc.forkRun(1L, paused.runId());
        assertThat(forked).isNotNull().startsWith("run_").isNotEqualTo(paused.runId());
        assertThat(mem).containsKey(forked);
    }

    @Test
    void step_pureTransitions() {
        AgentState created = new AgentState("r", 0, "created", Map.of("topic", "T"), null, false);
        AgentState analyzing = CheckpointTaskService.STEP.apply(created);
        assertThat(analyzing.currentStep()).isEqualTo("analyzing");

        AgentState awaiting = CheckpointTaskService.STEP.apply(analyzing);
        assertThat(awaiting.currentStep()).isEqualTo("awaiting_direction");
        assertThat(awaiting.paused()).isTrue();

        AgentState resumed = new AgentState("r", 2, "resumed", Map.of("topic", "T", "userDecision", "D"), null, false);
        AgentState finalizing = CheckpointTaskService.STEP.apply(resumed);
        assertThat(finalizing.currentStep()).isEqualTo("finalizing");

        AgentState done = CheckpointTaskService.STEP.apply(finalizing);
        assertThat(done.currentStep()).isEqualTo("done");
        assertThat(done.isTerminal()).isTrue();
    }

    @Test
    void disabled_reflectsInFlag() {
        CheckpointTaskService svc = newService(1L);
        props.setEnabled(false);
        assertThat(svc.isEnabled()).isFalse();
    }
}
