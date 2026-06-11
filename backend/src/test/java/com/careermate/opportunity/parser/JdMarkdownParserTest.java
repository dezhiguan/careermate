package com.careermate.opportunity.parser;

import com.careermate.opportunity.dto.ParsedJd;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdMarkdownParserTest {

    private static final String FULL_SAMPLE = """
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

    private final JdMarkdownParser parser = new JdMarkdownParser();

    @Test
    void parseFullStructuredJd() {
        ParsedJd parsed = parser.parse(FULL_SAMPLE);

        assertEquals("星天科技", parsed.company());
        assertEquals("算法工程师", parsed.title());
        assertNull(parsed.level());
        assertEquals("北京", parsed.city());
        assertEquals("1-3年", parsed.experienceRange());
        assertEquals(1, parsed.experienceMin());
        assertEquals(3, parsed.experienceMax());
        assertEquals("硕士", parsed.education());
        assertEquals("100-499人", parsed.companySize());
        assertEquals(LocalDate.of(2026, 6, 9), parsed.publishedAt());
        assertEquals(List.of("算法设计"), parsed.skills());
        assertTrue(parsed.jobDescription().contains("信号处理算法"));
        assertNull(parsed.salaryRange());
        assertNull(parsed.salaryMin());
        assertNull(parsed.salaryMax());
    }

    @Test
    void parseTitleMissingLevelSegment() {
        String markdown = """
                # 【JD】星天科技 | 算法工程师 | 北京
                **公司**:星天科技
                **城市**:北京
                """;
        ParsedJd parsed = parser.parse(markdown);

        assertNull(parsed.level());
        assertEquals("算法工程师", parsed.title());
        assertEquals("北京", parsed.city());
    }

    @Test
    void parseTitleOnlyTwoSegmentsCityFromMeta() {
        String markdown = """
                # 【JD】星天科技 | 算法工程师
                **城市**:上海
                """;
        ParsedJd parsed = parser.parse(markdown);

        assertEquals("星天科技", parsed.company());
        assertEquals("算法工程师", parsed.title());
        assertEquals("上海", parsed.city());
    }

    @Test
    void parseCompanyMissingUsesTitleFirstSegment() {
        String markdown = """
                # 【JD】星天科技 | 算法工程师 |  | 北京
                **城市**:北京
                """;
        ParsedJd parsed = parser.parse(markdown);

        assertEquals("星天科技", parsed.company());
        assertEquals("北京", parsed.city());
    }

    @Test
    void parseMissingExperienceField() {
        ParsedJd parsed = parser.parse("""
                # 【JD】星天科技 | 算法工程师
                **公司**:星天科技
                """);

        assertNull(parsed.experienceRange());
        assertNull(parsed.experienceMin());
        assertNull(parsed.experienceMax());
    }

    @Test
    void parseExperienceFreshGraduate() {
        ParsedJd parsed = parser.parse("""
                # 【JD】星天科技 | 算法工程师
                **经验**:应届
                """);

        assertEquals("应届", parsed.experienceRange());
        assertEquals(0, parsed.experienceMin());
        assertEquals(0, parsed.experienceMax());
    }

    @Test
    void parseExperienceUnlimited() {
        ParsedJd parsed = parser.parse("""
                # 【JD】星天科技 | 算法工程师
                **经验**:不限
                """);

        assertEquals("不限", parsed.experienceRange());
        assertNull(parsed.experienceMin());
        assertNull(parsed.experienceMax());
    }

    @Test
    void parseExperienceFiveToTenYears() {
        ParsedJd parsed = parser.parse("""
                # 【JD】星天科技 | 算法工程师
                **经验**:5-10年
                """);

        assertEquals("5-10年", parsed.experienceRange());
        assertEquals(5, parsed.experienceMin());
        assertEquals(10, parsed.experienceMax());
    }

    @Test
    void parseSkillsFilterEducationAndYearsNoise() {
        assertEquals(
                List.of("Java", "Redis"),
                JdMarkdownParser.parseSkills("1-3年, 硕士, Java, Redis")
        );
    }

    @Test
    void parseSkillsAllNoiseReturnsEmptyList() {
        assertEquals(
                List.of(),
                JdMarkdownParser.parseSkills("1-3年, 硕士, 本科, 不限")
        );
    }

    @Test
    void parseUnstructuredSingleLine() {
        ParsedJd parsed = parser.parse("随便一行没有结构的文本");

        assertNull(parsed.company());
        assertNull(parsed.title());
        assertNull(parsed.city());
        assertEquals(List.of(), parsed.skills());
    }

    @Test
    void parseSpecialCharactersDoesNotThrow() {
        String markdown = """
                # 【JD】星天科技 🚀 | 算法工程师：信号处理 |  | 北京。
                > 📅 2026-06-09 · Boss直聘 · 🟢 最新(3个月内)
                **公司**：星天科技（总部）
                **技术标签**：Java, Redis, 🟢标签
                """;

        assertDoesNotThrow(() -> parser.parse(markdown));
        ParsedJd parsed = parser.parse(markdown);
        assertEquals("星天科技（总部）", parsed.company());
        assertTrue(parsed.skills().contains("Java"));
    }
}
