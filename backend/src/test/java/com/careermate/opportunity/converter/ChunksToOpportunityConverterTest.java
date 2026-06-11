package com.careermate.opportunity.converter;

import com.careermate.opportunity.dto.OpportunityListItemVO;
import com.careermate.ragforge.RagForgeChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChunksToOpportunityConverterTest {

    private static final String SAMPLE_JD = """
            # 【JD】星天科技 | 算法工程师 |  | 北京
            > 📅 2026-06-09 · Boss直聘 · 🟢 最新(3个月内)

            **公司**:星天科技
            **城市**:北京
            **发布时间**:2026-06-09
            **经验**:1-3年
            **学历**:硕士
            **规模**:100-499人
            **技术标签**:1-3年, 硕士, 算法设计

            ## 职位描述
            工作职责:
            1.负责声呐装备的信号处理算法...
            """;

    private final ChunksToOpportunityConverter converter = new ChunksToOpportunityConverter();

    @Test
    void mergeChunksByChunkIdAscending() {
        List<RagForgeChunk> chunks = List.of(
                new RagForgeChunk(30L, 12345L, "file.md", "尾部描述", "JD", 0.5),
                new RagForgeChunk(10L, 12345L, "file.md", SAMPLE_JD, "JD", 0.7),
                new RagForgeChunk(20L, 12345L, "file.md", "中间补充", "JD", 0.6)
        );

        OpportunityListItemVO item = converter.convert(chunks).get(0);

        assertEquals("doc-12345", item.jdId());
        assertEquals(12345L, item.docId());
        assertEquals("星天科技", item.company());
        assertEquals("算法工程师", item.title());
        assertNull(item.level());
        assertEquals("北京", item.city());
        assertEquals("1-3年", item.experienceRange());
        assertEquals(1, item.experienceMin());
        assertEquals(3, item.experienceMax());
        assertEquals("硕士", item.education());
        assertEquals("100-499人", item.companySize());
        assertEquals("2026-06-09", item.publishedAt());
        assertEquals(List.of("算法设计"), item.skills());
    }

    @Test
    void ragScoreUsesMaxFinalScore() {
        List<RagForgeChunk> chunks = List.of(
                new RagForgeChunk(1L, 99L, "a.md", SAMPLE_JD, "JD", 0.42),
                new RagForgeChunk(2L, 99L, "a.md", "extra", "JD", 0.87)
        );

        OpportunityListItemVO item = converter.convert(chunks).get(0);
        assertEquals(0.87, item.ragScore());
    }

    @Test
    void sortByRagScoreDescending() {
        List<RagForgeChunk> chunks = List.of(
                new RagForgeChunk(1L, 1L, "low.md", SAMPLE_JD, "JD", 0.2),
                new RagForgeChunk(2L, 2L, "high.md", SAMPLE_JD, "JD", 0.9),
                new RagForgeChunk(3L, 3L, "mid.md", SAMPLE_JD, "JD", 0.5)
        );

        List<OpportunityListItemVO> items = converter.convert(chunks);

        assertEquals(2L, items.get(0).docId());
        assertEquals(3L, items.get(1).docId());
        assertEquals(1L, items.get(2).docId());
    }

    @Test
    void unstructuredContentFallsBackTitleToFilename() {
        List<RagForgeChunk> chunks = List.of(
                new RagForgeChunk(1L, 7L, "fallback-title.md", "只有一行普通文本", "JD", 0.3)
        );

        OpportunityListItemVO item = converter.convert(chunks).get(0);

        assertNull(item.company());
        assertEquals("fallback-title.md", item.title());
        assertEquals(List.of(), item.skills());
    }
}
