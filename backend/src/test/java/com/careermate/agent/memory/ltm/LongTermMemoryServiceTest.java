package com.careermate.agent.memory.ltm;

import com.careermate.mapper.UserLongTermMemoryMapper;
import com.careermate.model.entity.UserLongTermMemoryEntity;
import com.careermate.ragforge.RagForgeChunk;
import com.careermate.ragforge.RagForgeClient;
import com.careermate.ragforge.RagForgeProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
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
    private final RagForgeClient ragForgeClient = mock(RagForgeClient.class);
    private final RagForgeProperties ragProps = new RagForgeProperties();
    private final LtmProperties props = new LtmProperties();
    private final LongTermMemoryService service =
            new LongTermMemoryService(embed, mapper, props, ragForgeClient, ragProps);

    private LtmMatch match(long id, String type, String text, double score, double conf) {
        LtmMatch m = new LtmMatch();
        m.setId(id);
        m.setFactType(type);
        m.setFactText(text);
        m.setScore(score);
        m.setConfidence(conf);
        return m;
    }

    private UserLongTermMemoryEntity fact(long id, String type, String text, long ragDocId, double conf) {
        UserLongTermMemoryEntity e = new UserLongTermMemoryEntity();
        e.setId(id);
        e.setUserId(7L);
        e.setFactType(type);
        e.setFactText(text);
        e.setRagDocId(ragDocId);
        e.setConfidence(conf);
        e.setDeleted(false);
        return e;
    }

    private RagForgeChunk chunk(long docId, String content, double score) {
        return new RagForgeChunk(docId, docId, "mem", content, "PREFERENCE", score);
    }

    // ============================================================ KB backend（默认）

    @Test
    void kb_recall_scopesByUserDocIds_andFiltersThreshold() {
        props.setEnabled(true);              // storage 默认 KB
        props.setRecallThreshold(0.7);
        ragProps.setLtmKbId("42");
        when(mapper.selectList(any())).thenReturn(List.of(
                fact(1L, "PREFERENCE", "想远程", 900L, 0.6),
                fact(2L, "SKILL", "会Java", 901L, 0.6)));
        when(ragForgeClient.searchInKb(anyLong(), anyString(), anyInt(), anyList(), any()))
                .thenReturn(List.of(
                        chunk(900L, "想远程", 0.91),
                        chunk(901L, "会Java", 0.5)));   // 低于阈值被过滤

        List<LtmMatch> r = service.recall(7L, "工作方式");

        assertThat(r).hasSize(1);
        assertThat(r.get(0).getFactText()).isEqualTo("想远程");
        assertThat(r.get(0).getId()).isEqualTo(1L);
    }

    @Test
    void kb_recall_noKbId_returnsEmpty() {
        props.setEnabled(true);
        ragProps.setLtmKbId("");             // 未配库
        assertThat(service.recall(7L, "x")).isEmpty();
    }

    @Test
    void kb_recall_noActiveDocs_returnsEmpty() {
        props.setEnabled(true);
        ragProps.setLtmKbId("42");
        when(mapper.selectList(any())).thenReturn(List.of());
        assertThat(service.recall(7L, "x")).isEmpty();
    }

    @Test
    void kb_store_newFact_syncsAndInsertsWithoutSupersede() {
        props.setEnabled(true);
        ragProps.setLtmKbId("42");
        when(mapper.selectList(any())).thenReturn(List.of());   // 无同 type 旧 fact
        when(ragForgeClient.ingestText(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(950L));

        boolean ok = service.store(7L, "PREFERENCE", "我只想远程", 0.6);

        assertThat(ok).isTrue();
        verify(ragForgeClient).ingestText(eq(42L), anyString(), eq("我只想远程"), eq("PREFERENCE"));
        verify(mapper).insert(any(UserLongTermMemoryEntity.class));
        verify(mapper, never()).markSuperseded(anyLong(), anyLong());
        verify(ragForgeClient, never()).deleteDocument(anyLong());
    }

    @Test
    void kb_store_highSimilarity_supersedesOldAndDeletesOldDoc() {
        props.setEnabled(true);
        props.setDuplicateThreshold(0.85);
        ragProps.setLtmKbId("42");
        when(mapper.selectList(any())).thenReturn(List.of(fact(99L, "PREFERENCE", "想远程", 900L, 0.6)));
        when(ragForgeClient.searchInKb(anyLong(), anyString(), anyInt(), anyList(), any()))
                .thenReturn(List.of(chunk(900L, "想远程", 0.9)));   // 命中旧 fact
        when(ragForgeClient.ingestText(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(950L));
        when(mapper.insert(any(UserLongTermMemoryEntity.class))).thenAnswer(inv -> {
            inv.getArgument(0, UserLongTermMemoryEntity.class).setId(100L);
            return 1;
        });

        boolean ok = service.store(7L, "PREFERENCE", "想线下办公", 0.6);

        assertThat(ok).isTrue();
        verify(mapper).markSuperseded(eq(99L), eq(100L));
        verify(ragForgeClient).deleteDocument(900L);       // 旧文档从 KB 移除
    }

    @Test
    void kb_store_syncFails_returnsFalse() {
        props.setEnabled(true);
        ragProps.setLtmKbId("42");
        when(mapper.selectList(any())).thenReturn(List.of());
        when(ragForgeClient.ingestText(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        assertThat(service.store(7L, "PREFERENCE", "x", 0.6)).isFalse();
        verify(mapper, never()).insert(any(UserLongTermMemoryEntity.class));
    }

    @Test
    void kb_store_noKbId_returnsFalse() {
        props.setEnabled(true);
        ragProps.setLtmKbId("");
        assertThat(service.store(7L, "PREFERENCE", "x", 0.6)).isFalse();
    }

    @Test
    void kb_forget_deletesKbDocAndSoftDeletes() {
        props.setEnabled(true);
        ragProps.setLtmKbId("42");
        when(mapper.selectById(5L)).thenReturn(fact(5L, "PREFERENCE", "想远程", 900L, 0.6));

        service.forget(7L, 5L);

        verify(ragForgeClient).deleteDocument(900L);
        verify(mapper).softDelete(7L, 5L);
    }

    @Test
    void kb_forget_wrongUser_skipsKbDelete() {
        props.setEnabled(true);
        ragProps.setLtmKbId("42");
        UserLongTermMemoryEntity other = fact(5L, "PREFERENCE", "想远程", 900L, 0.6);
        other.setUserId(999L);
        when(mapper.selectById(5L)).thenReturn(other);

        service.forget(7L, 5L);

        verify(ragForgeClient, never()).deleteDocument(anyLong());
        verify(mapper).softDelete(7L, 5L);   // 软删仍带 userId 兜底
    }

    // ============================================================ PGVECTOR backend（回退）

    @Test
    void recall_disabled_returnsEmpty() {
        assertThat(service.recall(7L, "远程")).isEmpty();
    }

    @Test
    void pg_recall_filtersBelowThreshold() {
        props.setEnabled(true);
        props.setStorage("PGVECTOR");
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
    void pg_recall_noEmbedding_returnsEmpty() {
        props.setEnabled(true);
        props.setStorage("PGVECTOR");
        when(embed.embed(anyString())).thenReturn(Optional.empty());
        assertThat(service.recall(7L, "x")).isEmpty();
    }

    @Test
    void pg_store_newFact_insertsWithoutSupersede() {
        props.setEnabled(true);
        props.setStorage("PGVECTOR");
        when(embed.embed(anyString())).thenReturn(Optional.of(new float[]{0.1f, 0.2f}));
        when(mapper.nearestSameType(anyLong(), anyString(), anyString())).thenReturn(null);

        boolean ok = service.store(7L, "PREFERENCE", "我只想远程", 0.6);

        assertThat(ok).isTrue();
        verify(mapper).insertWithEmbedding(any(UserLongTermMemoryEntity.class));
        verify(mapper, never()).markSuperseded(anyLong(), anyLong());
    }

    @Test
    void pg_store_highSimilarity_supersedesOldAndBoosts() {
        props.setEnabled(true);
        props.setStorage("PGVECTOR");
        props.setDuplicateThreshold(0.85);
        when(embed.embed(anyString())).thenReturn(Optional.of(new float[]{0.1f}));
        when(mapper.nearestSameType(anyLong(), anyString(), anyString()))
                .thenReturn(match(99L, "PREFERENCE", "想远程", 0.9, 0.6));
        when(mapper.insertWithEmbedding(any())).thenAnswer(inv -> {
            inv.getArgument(0, UserLongTermMemoryEntity.class).setId(100L);
            return 1;
        });

        boolean ok = service.store(7L, "PREFERENCE", "想线下", 0.6);

        assertThat(ok).isTrue();
        verify(mapper).insertWithEmbedding(any(UserLongTermMemoryEntity.class));
        verify(mapper).markSuperseded(eq(99L), eq(100L));
    }

    @Test
    void store_disabledOrBlank_returnsFalse() {
        assertThat(service.store(7L, "PREFERENCE", "x", 0.6)).isFalse(); // disabled
        props.setEnabled(true);
        assertThat(service.store(7L, "PREFERENCE", "  ", 0.6)).isFalse(); // blank
    }
}
