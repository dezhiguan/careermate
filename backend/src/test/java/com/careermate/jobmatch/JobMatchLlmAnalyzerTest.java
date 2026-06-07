package com.careermate.jobmatch;

import com.careermate.llm.LlmClient;
import com.careermate.llm.LlmProperties;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.ragforge.RagForgeClient;
import com.careermate.ragforge.RagForgeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JobMatchLlmAnalyzerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final RagForgeClient ragNoop;

    JobMatchLlmAnalyzerTest() {
        RagForgeProperties p = new RagForgeProperties();
        p.setEnabled(false);
        this.ragNoop = new RagForgeClient(p);
    }

    private LlmProperties props(String provider) {
        LlmProperties p = new LlmProperties();
        p.setProvider(provider);
        return p;
    }

    @Test
    void mockProviderReturnsEmpty() {
        LlmClient mockLlm = mock(LlmClient.class);
        JobMatchLlmAnalyzer analyzer = new JobMatchLlmAnalyzer(mockLlm, props("mock"), mapper, ragNoop);
        Optional<JobMatchStructuredResult> r = analyzer.tryAnalyze("resume", "jd", "后端");
        assertTrue(r.isEmpty());
        verify(mockLlm, never()).chat(any());
    }

    @Test
    void validLlmJsonParsesCorrectly() {
        LlmClient mockLlm = mock(LlmClient.class);
        String json = """
            { "matchScore": 78, "matchLevel": "MEDIUM",
              "matchedSkills": ["Java","Kafka"], "missingSkills": ["K8s"],
              "strengths": ["有分布式经验"], "risks": ["缺少 K8s"],
              "suggestions": ["补 K8s"], "analysisSummary": "中等匹配" }
            """;
        when(mockLlm.chat(any(ChatRequest.class)))
            .thenReturn(ChatResponse.builder().content(json).build());
        JobMatchLlmAnalyzer analyzer = new JobMatchLlmAnalyzer(mockLlm, props("qwen"), mapper, ragNoop);
        Optional<JobMatchStructuredResult> r = analyzer.tryAnalyze("我用过 Kafka", "需要 Java", "后端");
        assertTrue(r.isPresent());
        assertEquals(78, r.get().matchScore());
        assertEquals("MEDIUM", r.get().matchLevel());
        assertTrue(r.get().matchedSkills().contains("Java"));
    }

    @Test
    void llmReturnsNonJsonFallback() {
        LlmClient mockLlm = mock(LlmClient.class);
        when(mockLlm.chat(any(ChatRequest.class)))
            .thenReturn(ChatResponse.builder().content("我不知道怎么分析").build());
        JobMatchLlmAnalyzer analyzer = new JobMatchLlmAnalyzer(mockLlm, props("qwen"), mapper, ragNoop);
        assertTrue(analyzer.tryAnalyze("r", "j", "后端").isEmpty());
    }

    @Test
    void llmThrowsFallback() {
        LlmClient mockLlm = mock(LlmClient.class);
        when(mockLlm.chat(any(ChatRequest.class)))
            .thenThrow(new RuntimeException("network timeout"));
        JobMatchLlmAnalyzer analyzer = new JobMatchLlmAnalyzer(mockLlm, props("qwen"), mapper, ragNoop);
        assertTrue(analyzer.tryAnalyze("r", "j", "后端").isEmpty());
    }

    @Test
    void invalidJsonSchemaFallback() {
        LlmClient mockLlm = mock(LlmClient.class);
        String badJson = """
            { "matchScore": 150, "matchLevel": "UNKNOWN" }
            """;
        when(mockLlm.chat(any(ChatRequest.class)))
            .thenReturn(ChatResponse.builder().content(badJson).build());
        JobMatchLlmAnalyzer analyzer = new JobMatchLlmAnalyzer(mockLlm, props("qwen"), mapper, ragNoop);
        assertTrue(analyzer.tryAnalyze("r", "j", "后端").isEmpty());
    }
}
