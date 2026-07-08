package com.careermate.agent.memory.ltm;

import com.careermate.mapper.UserLongTermMemoryMapper;
import com.careermate.model.entity.UserLongTermMemoryEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LongTermMemoryServiceTest {

    private final EmbeddingClient embed = mock(EmbeddingClient.class);
    private final UserLongTermMemoryMapper mapper = mock(UserLongTermMemoryMapper.class);
    private final LtmProperties props = new LtmProperties();
    private final LongTermMemoryService service = new LongTermMemoryService(embed, mapper, props);

    private LtmMatch match(long id, String type, String text, double score, double conf) {
        LtmMatch m = new LtmMatch();
        m.setId(id);
        m.setFactType(type);
        m.setFactText(text);
        m.setScore(score);
        m.setConfidence(conf);
        return m;
    }

    // ---- recall ----

    @Test
    void recall_disabled_returnsEmpty() {
        assertThat(service.recall(7L, "远程")).isEmpty();
    }

    @Test
    void recall_filtersBelowThreshold() {
        props.setEnabled(true);
        props.setRecallThreshold(0.7);
        when(embed.embed(anyString())).thenReturn(Optional.of(new float[]{0.1f}));
        when(mapper.cosineTopK(anyLong(), anyString(), anyInt())).thenReturn(List.of(
                match(1, "PREFERENCE", "想远程", 0.9, 0.6),
                match(2, "SKILL", "会Java", 0.5, 0.6)));   // 低于阈值被过滤

        List<LtmMatch> r = service.recall(7L, "工作方式");
        assertThat(r).hasSize(1);
        assertThat(r.get(0).getFactText()).isEqualTo("想远程");
    }

    @Test
    void recall_noEmbedding_returnsEmpty() {
        props.setEnabled(true);
        when(embed.embed(anyString())).thenReturn(Optional.empty());
        assertThat(service.recall(7L, "x")).isEmpty();
    }

    // ---- store ----

    @Test
    void store_newFact_insertsWithoutSupersede() {
        props.setEnabled(true);
        when(embed.embed(anyString())).thenReturn(Optional.of(new float[]{0.1f, 0.2f}));
        when(mapper.nearestSameType(anyLong(), anyString(), anyString())).thenReturn(null);

        boolean ok = service.store(7L, "PREFERENCE", "我只想远程", 0.6);

        assertThat(ok).isTrue();
        verify(mapper).insertWithEmbedding(any(UserLongTermMemoryEntity.class));
        verify(mapper, never()).markSuperseded(anyLong(), anyLong());
    }

    @Test
    void store_highSimilarity_supersedesOldAndBoosts() {
        props.setEnabled(true);
        props.setDuplicateThreshold(0.85);
        when(embed.embed(anyString())).thenReturn(Optional.of(new float[]{0.1f}));
        when(mapper.nearestSameType(anyLong(), anyString(), anyString()))
                .thenReturn(match(99L, "PREFERENCE", "想远程", 0.9, 0.6));
        // 模拟 insert 回填 id
        when(mapper.insertWithEmbedding(any())).thenAnswer(inv -> {
            inv.getArgument(0, UserLongTermMemoryEntity.class).setId(100L);
            return 1;
        });

        boolean ok = service.store(7L, "PREFERENCE", "想线下", 0.6);

        assertThat(ok).isTrue();
        verify(mapper).insertWithEmbedding(any(UserLongTermMemoryEntity.class));
        verify(mapper).markSuperseded(eq(99L), eq(100L)); // supersede 旧 fact
    }

    @Test
    void store_disabledOrBlank_returnsFalse() {
        assertThat(service.store(7L, "PREFERENCE", "x", 0.6)).isFalse(); // disabled
        props.setEnabled(true);
        assertThat(service.store(7L, "PREFERENCE", "  ", 0.6)).isFalse(); // blank
    }
}
