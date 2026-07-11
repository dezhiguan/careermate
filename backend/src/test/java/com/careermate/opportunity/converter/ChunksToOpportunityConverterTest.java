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
        // 评审 P0-2：文件名兜底标题需去掉 .md 后缀，不把源文件名泄露到界面
        assertEquals("fallback-title", item.title());
        assertEquals(List.of(), item.skills());
    }

    @Test
    void sanitizeTitleStripsInternalMarkersAndMdSuffix() {
        // .md 后缀
        assertEquals("北京·Java后端·25-40K", ChunksToOpportunityConverter.sanitizeTitle("北京·Java后端·25-40K.md"));
        // 开头【JD】标记 + .md
        assertEquals("杭州后端开发", ChunksToOpportunityConverter.sanitizeTitle("【JD】杭州后端开发.md"));
        // 开头【J0】内部编号
        assertEquals("某大型互联网公司", ChunksToOpportunityConverter.sanitizeTitle("【J0】某大型互联网公司.MD"));
        // markdown 标题符号 + 连续标记
        assertEquals("算法工程师", ChunksToOpportunityConverter.sanitizeTitle("# 【JD】【J1】算法工程师"));
        // 干净标题保持不变
        assertEquals("Java 后端工程师", ChunksToOpportunityConverter.sanitizeTitle("  Java 后端工程师  "));
        // null 安全
        assertNull(ChunksToOpportunityConverter.sanitizeTitle(null));
    }
}
