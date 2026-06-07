package com.careermate.jobmatch;

import com.careermate.llm.LlmClient;
import com.careermate.llm.LlmProperties;
import com.careermate.ragforge.RagForgeClient;
import com.careermate.ragforge.RagForgeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class JobMatchAnalyzerTest {

    private final JobMatchAnalyzer analyzer;

    JobMatchAnalyzerTest() {
        RagForgeClient ragClient = new RagForgeClient(new RagForgeProperties());
        LlmProperties llmProps = new LlmProperties();
        llmProps.setProvider("mock");
        JobMatchLlmAnalyzer llmAnalyzer =
            new JobMatchLlmAnalyzer(mock(LlmClient.class), llmProps, new ObjectMapper(), ragClient);
        analyzer = new JobMatchAnalyzer(ragClient, llmAnalyzer);
    }

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
