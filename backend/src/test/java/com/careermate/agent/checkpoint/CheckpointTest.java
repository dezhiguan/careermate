package com.careermate.agent.checkpoint;

import com.careermate.mapper.AgentCheckpointMapper;
import com.careermate.mapper.AgentRunMapper;
import com.careermate.model.entity.AgentCheckpointEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CheckpointTest {

    private final StateCodec codec = new StateCodec(new ObjectMapper());

    // ---- StateCodec ----

    @Test
    void codec_roundtrip() {
        AgentState s = new AgentState("run1", 3, "act", Map.of("k", "v", "n", 5), "send_resume", false);
        AgentState back = codec.deserialize(codec.serialize(s));
        assertThat(back.runId()).isEqualTo("run1");
        assertThat(back.stepIndex()).isEqualTo(3);
        assertThat(back.currentStep()).isEqualTo("act");
        assertThat(back.pendingAction()).isEqualTo("send_resume");
    }

    @Test
    void codec_compressesRepetitiveData() {
        byte[] raw = "abcabcabcabc".repeat(200).getBytes();
        byte[] c = StateCodec.compress(raw);
        assertThat(c.length).isLessThan(raw.length / 2);   // 压缩比 > 50%
        assertThat(StateCodec.decompress(c)).isEqualTo(raw);
    }

    @Test
    void codec_hashDeterministic() {
        byte[] blob = codec.serialize(AgentState.initial("r"));
        assertThat(codec.hash(blob)).isEqualTo(codec.hash(blob));
    }

    // ---- CheckpointStore ----

    @Test
    void store_savesWhenNoPrior() {
        AgentCheckpointMapper mapper = mock(AgentCheckpointMapper.class);
        when(mapper.findLatest(anyString())).thenReturn(null);
        CheckpointStore store = new CheckpointStore(mapper, codec);

        boolean wrote = store.save("run1", "act", AgentState.initial("run1"), false, null);
        assertThat(wrote).isTrue();
        verify(mapper).insert(any(AgentCheckpointEntity.class));
    }

    @Test
    void store_dedupsIdenticalAdjacentState() {
        AgentCheckpointMapper mapper = mock(AgentCheckpointMapper.class);
        AgentState state = AgentState.initial("run1");
        byte[] blob = codec.serialize(state);
        AgentCheckpointEntity prior = new AgentCheckpointEntity();
        prior.setStateHash(codec.hash(blob));
        when(mapper.findLatest("run1")).thenReturn(prior);
        CheckpointStore store = new CheckpointStore(mapper, codec);

        boolean wrote = store.save("run1", "act", state, false, null);
        assertThat(wrote).isFalse();
        verify(mapper, never()).insert(any(AgentCheckpointEntity.class));
    }

    @Test
    void store_loadLatestDeserializes() {
        AgentCheckpointMapper mapper = mock(AgentCheckpointMapper.class);
        AgentCheckpointEntity e = new AgentCheckpointEntity();
        e.setStateBlob(codec.serialize(new AgentState("run1", 2, "reflect", Map.of(), null, false)));
        when(mapper.findLatest("run1")).thenReturn(e);
        CheckpointStore store = new CheckpointStore(mapper, codec);

        assertThat(store.loadLatest("run1")).get()
                .extracting(AgentState::currentStep).isEqualTo("reflect");
    }

    // ---- CheckpointedAgentEngine ----

    private CheckpointedAgentEngine engine(CheckpointStore store) {
        return new CheckpointedAgentEngine(store, mock(AgentRunMapper.class));
    }

    @Test
    void engine_runsToTerminal() {
        CheckpointStore store = mock(CheckpointStore.class);
        when(store.loadLatest(anyString())).thenReturn(java.util.Optional.empty());
        // 第 2 步进入 done
        AgentState done = engine(store).runOrResume("run1", 7L, s ->
                s.stepIndex() >= 1 ? new AgentState("run1", s.stepIndex() + 1, "done", Map.of(), null, false)
                        : s.advance("act", Map.of()));
        assertThat(done.isTerminal()).isTrue();
        // init 快照 + 2 步推进 = 3 次 save
        verify(store, times(3)).save(anyString(), any(), any(), org.mockito.ArgumentMatchers.eq(false), any());
    }

    @Test
    void engine_pausesForHitl() {
        CheckpointStore store = mock(CheckpointStore.class);
        when(store.loadLatest(anyString())).thenReturn(java.util.Optional.empty());
        AgentState paused = engine(store).runOrResume("run1", 7L, s -> s.pause("send_resume"));
        assertThat(paused.paused()).isTrue();
        assertThat(paused.pendingAction()).isEqualTo("send_resume");
    }

    @Test
    void engine_fork_createsNewRunFromCheckpoint() {
        CheckpointStore store = mock(CheckpointStore.class);
        when(store.loadLatest("src"))
                .thenReturn(java.util.Optional.of(new AgentState("src", 2, "reflect", Map.of("k", "v"), null, false)));
        AgentRunMapper runMapper = mock(AgentRunMapper.class);
        CheckpointedAgentEngine eng = new CheckpointedAgentEngine(store, runMapper);

        String newRunId = eng.fork("src", 7L);

        assertThat(newRunId).isNotNull().startsWith("run_");
        verify(runMapper).insert(any(com.careermate.model.entity.AgentRunEntity.class));
        verify(store).save(org.mockito.ArgumentMatchers.eq(newRunId), anyString(), any(), org.mockito.ArgumentMatchers.eq(false), any());
    }

    @Test
    void engine_fork_nullWhenNoCheckpoint() {
        CheckpointStore store = mock(CheckpointStore.class);
        when(store.loadLatest("src")).thenReturn(java.util.Optional.empty());
        assertThat(engine(store).fork("src", 7L)).isNull();
    }

    @Test
    void engine_resumeContinuesToDone() {
        CheckpointStore store = mock(CheckpointStore.class);
        when(store.loadLatest("run1"))
                .thenReturn(java.util.Optional.of(new AgentState("run1", 2, "paused", Map.of(), "send_resume", true)));
        AgentState result = engine(store).resume("run1", 7L, "同意", s ->
                new AgentState("run1", s.stepIndex() + 1, "done", Map.of(), null, false));
        assertThat(result.isTerminal()).isTrue();
    }
}
