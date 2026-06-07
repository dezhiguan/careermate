package com.careermate.interview;

import com.careermate.jobmatch.JobMatchJsonSupport;
import com.careermate.llm.LlmClient;
import com.careermate.llm.LlmProperties;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.model.entity.JobMatchEntity;
import com.careermate.model.entity.ResumeEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InterviewLlmQuestionGeneratorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JobMatchJsonSupport jsonSupport = new JobMatchJsonSupport(mapper);

    private LlmProperties props(String provider) {
        LlmProperties p = new LlmProperties();
        p.setProvider(provider);
        return p;
    }

    private ResumeEntity sampleResume() {
        ResumeEntity r = new ResumeEntity();
        r.setTitle("Java 后端简历");
        r.setContent("3 年 Java 后端经验，熟悉 Spring Boot、Kafka、Redis。负责消息推送系统重构。");
        return r;
    }

    private Optional<JobMatchEntity> sampleJobMatch() {
        JobMatchEntity m = new JobMatchEntity();
        m.setJobTitle("高级 Java 后端");
        m.setCompanyName("字节跳动");
        m.setJdContent("分布式系统经验，消息中间件");
        m.setMatchedSkills("[\"Java\",\"Kafka\"]");
        m.setMissingSkills("[\"Kubernetes\"]");
        return Optional.of(m);
    }

    @Test
    void mockProviderReturnsEmpty() {
        LlmClient mockLlm = mock(LlmClient.class);
        InterviewLlmQuestionGenerator g =
            new InterviewLlmQuestionGenerator(mockLlm, props("mock"), mapper, jsonSupport);
        assertTrue(g.tryGenerate(sampleResume(), sampleJobMatch()).isEmpty());
        verify(mockLlm, never()).chat(any());
    }

    @Test
    void validLlmJsonParsesCorrectly() {
        LlmClient mockLlm = mock(LlmClient.class);
        String json = """
            {
              "questions": [
                {"questionNo":1,"questionType":"PROJECT","questionText":"介绍消息推送系统重构","referencePoints":["架构","难点"]},
                {"questionNo":2,"questionType":"SKILL","questionText":"Kafka exactly-once 怎么实现","referencePoints":["事务","幂等"]},
                {"questionNo":3,"questionType":"GAP","questionText":"如何学习 K8s","referencePoints":["路径","实践"]},
                {"questionNo":4,"questionType":"SYSTEM_DESIGN","questionText":"设计抖音消息推送","referencePoints":["容量","可用性"]},
                {"questionNo":5,"questionType":"BEHAVIOR","questionText":"协作冲突如何解决","referencePoints":["案例","结果"]}
              ]
            }
            """;
        when(mockLlm.chat(any(ChatRequest.class)))
            .thenReturn(ChatResponse.builder().content(json).build());
        InterviewLlmQuestionGenerator g =
            new InterviewLlmQuestionGenerator(mockLlm, props("qwen"), mapper, jsonSupport);
        Optional<List<GeneratedQuestionList.LlmQuestion>> r = g.tryGenerate(sampleResume(), sampleJobMatch());
        assertTrue(r.isPresent());
        assertEquals(5, r.get().size());
        assertEquals("PROJECT", r.get().get(0).questionType());
        assertEquals(5, r.get().get(4).questionNo());
    }

    @Test
    void wrongQuestionCountFallback() {
        LlmClient mockLlm = mock(LlmClient.class);
        String json = """
            { "questions": [
                {"questionNo":1,"questionType":"PROJECT","questionText":"t1","referencePoints":[]},
                {"questionNo":2,"questionType":"SKILL","questionText":"t2","referencePoints":[]}
            ]}
            """;
        when(mockLlm.chat(any(ChatRequest.class)))
            .thenReturn(ChatResponse.builder().content(json).build());
        InterviewLlmQuestionGenerator g =
            new InterviewLlmQuestionGenerator(mockLlm, props("qwen"), mapper, jsonSupport);
        assertTrue(g.tryGenerate(sampleResume(), sampleJobMatch()).isEmpty());
    }

    @Test
    void invalidQuestionTypeFallback() {
        LlmClient mockLlm = mock(LlmClient.class);
        String json = """
            { "questions": [
                {"questionNo":1,"questionType":"PROJECT","questionText":"t1","referencePoints":[]},
                {"questionNo":2,"questionType":"WEIRD","questionText":"t2","referencePoints":[]},
                {"questionNo":3,"questionType":"GAP","questionText":"t3","referencePoints":[]},
                {"questionNo":4,"questionType":"SYSTEM_DESIGN","questionText":"t4","referencePoints":[]},
                {"questionNo":5,"questionType":"BEHAVIOR","questionText":"t5","referencePoints":[]}
            ]}
            """;
        when(mockLlm.chat(any(ChatRequest.class)))
            .thenReturn(ChatResponse.builder().content(json).build());
        InterviewLlmQuestionGenerator g =
            new InterviewLlmQuestionGenerator(mockLlm, props("qwen"), mapper, jsonSupport);
        assertTrue(g.tryGenerate(sampleResume(), sampleJobMatch()).isEmpty());
    }

    @Test
    void llmThrowsFallback() {
        LlmClient mockLlm = mock(LlmClient.class);
        when(mockLlm.chat(any(ChatRequest.class)))
            .thenThrow(new RuntimeException("timeout"));
        InterviewLlmQuestionGenerator g =
            new InterviewLlmQuestionGenerator(mockLlm, props("qwen"), mapper, jsonSupport);
        assertTrue(g.tryGenerate(sampleResume(), sampleJobMatch()).isEmpty());
    }
}
