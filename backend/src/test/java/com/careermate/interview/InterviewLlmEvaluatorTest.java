package com.careermate.interview;

import com.careermate.llm.LlmClient;
import com.careermate.llm.LlmProperties;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.model.entity.InterviewQuestionEntity;
import com.careermate.ragforge.RagForgeClient;
import com.careermate.ragforge.RagForgeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InterviewLlmEvaluatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final RagForgeClient ragNoop;

    InterviewLlmEvaluatorTest() {
        RagForgeProperties p = new RagForgeProperties();
        p.setEnabled(false);
        this.ragNoop = new RagForgeClient(p);
    }

    private LlmProperties props(String provider) {
        LlmProperties p = new LlmProperties();
        p.setProvider(provider);
        return p;
    }

    private InterviewQuestionEntity sampleQuestion() {
        InterviewQuestionEntity q = new InterviewQuestionEntity();
        q.setQuestionType("PROJECT");
        q.setQuestionText("介绍你最有挑战的项目");
        return q;
    }

    @Test
    void mockProviderReturnsEmpty() {
        LlmClient mockLlm = mock(LlmClient.class);
        InterviewLlmEvaluator e = new InterviewLlmEvaluator(mockLlm, props("mock"), mapper, ragNoop);
        assertTrue(e.tryEvaluate(sampleQuestion(), "我用过 Kafka 做了消息队列削峰", List.of("项目", "难点")).isEmpty());
        verify(mockLlm, never()).chat(any());
    }

    @Test
    void validLlmJsonParsesCorrectly() {
        LlmClient mockLlm = mock(LlmClient.class);
        String json = """
            { "score": 82,
              "feedback": "回答结构清晰，覆盖了核心要点，建议补充量化指标。",
              "strengths": ["结构清晰", "覆盖核心要点"],
              "improvements": ["缺少量化数据"] }
            """;
        when(mockLlm.chat(any(ChatRequest.class)))
            .thenReturn(ChatResponse.builder().content(json).build());
        InterviewLlmEvaluator ev = new InterviewLlmEvaluator(mockLlm, props("qwen"), mapper, ragNoop);
        Optional<EvaluationStructuredResult> r = ev.tryEvaluate(sampleQuestion(), "有详细回答...", List.of("项目", "难点"));
        assertTrue(r.isPresent());
        assertEquals(82, r.get().score());
        assertTrue(r.get().strengths().contains("结构清晰"));
    }

    @Test
    void llmReturnsNonJsonFallback() {
        LlmClient mockLlm = mock(LlmClient.class);
        when(mockLlm.chat(any(ChatRequest.class)))
            .thenReturn(ChatResponse.builder().content("呃，我看不出问题").build());
        InterviewLlmEvaluator ev = new InterviewLlmEvaluator(mockLlm, props("qwen"), mapper, ragNoop);
        assertTrue(ev.tryEvaluate(sampleQuestion(), "ans", List.of()).isEmpty());
    }

    @Test
    void llmThrowsFallback() {
        LlmClient mockLlm = mock(LlmClient.class);
        when(mockLlm.chat(any(ChatRequest.class)))
            .thenThrow(new RuntimeException("timeout"));
        InterviewLlmEvaluator ev = new InterviewLlmEvaluator(mockLlm, props("qwen"), mapper, ragNoop);
        assertTrue(ev.tryEvaluate(sampleQuestion(), "ans", List.of()).isEmpty());
    }

    @Test
    void invalidJsonSchemaFallback() {
        LlmClient mockLlm = mock(LlmClient.class);
        String badJson = """
            { "score": 200, "feedback": "" }
            """;
        when(mockLlm.chat(any(ChatRequest.class)))
            .thenReturn(ChatResponse.builder().content(badJson).build());
        InterviewLlmEvaluator ev = new InterviewLlmEvaluator(mockLlm, props("qwen"), mapper, ragNoop);
        assertTrue(ev.tryEvaluate(sampleQuestion(), "ans", List.of()).isEmpty());
    }
}
