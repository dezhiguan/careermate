package com.careermate.jobmatch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobMatchAnalyzerTest {

    private final JobMatchAnalyzer analyzer =
            new JobMatchAnalyzer(new com.careermate.ragforge.RagForgeClient(new com.careermate.ragforge.RagForgeProperties()));

    @Test
    void computesMatchedAndMissingSkills() {
        String resume = "熟悉 Java, Spring Boot, PostgreSQL, Redis, RAG 项目经验";
        String jd = "要求 Java, Spring Boot, Redis, Elasticsearch, Docker";

        JobMatchAnalysisResult result = analyzer.analyze(resume, jd, "Java 后端工程师");

        assertTrue(result.getMatchedSkills().contains("Java"));
        assertTrue(result.getMatchedSkills().contains("Spring Boot"));
        assertTrue(result.getMatchedSkills().contains("Redis"));
        assertTrue(result.getMissingSkills().contains("Elasticsearch"));
        assertTrue(result.getMissingSkills().contains("Docker"));
        assertTrue(result.getMatchScore() >= 0 && result.getMatchScore() <= 100);
        assertTrue(result.getAnalysisSummary().length() >= 50);
    }

    @Test
    void emptyJdSkillsUsesDefaultScoreBand() {
        JobMatchAnalysisResult result = analyzer.analyze("Java 开发", "负责业务开发，无具体技术栈", "通用岗位");
        assertEquals(50, result.getMatchScore());
    }
}
