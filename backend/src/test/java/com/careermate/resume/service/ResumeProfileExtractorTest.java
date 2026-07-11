package com.careermate.resume.service;

import com.careermate.llm.LlmClient;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.resume.service.ResumeProfileExtractor.ExtractedProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeProfileExtractorTest {

    @Mock
    private LlmClient llmClient;

    private ResumeProfileExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new ResumeProfileExtractor(llmClient, new ObjectMapper());
    }

    @Test
    void extractsProfileFromResumeJson() {
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .content("""
                        这是解析结果：{"targetRole":"Java后端工程师","targetCity":"广州","seniority":"3-5年","workMode":"全职","skillKeywords":["Java","Spring","MySQL"]}
                        """)
                .build());

        Optional<ExtractedProfile> result = extractor.extract("3年 Java 后端，广州，熟悉 Spring/MySQL");

        assertTrue(result.isPresent());
        ExtractedProfile p = result.get();
        assertEquals("Java后端工程师", p.targetRole());
        assertEquals("广州", p.targetCity());
        assertEquals("3-5年", p.seniority());
        assertEquals("全职", p.workMode());
        assertEquals(3, p.skillKeywords().size());
    }

    @Test
    void blankContentReturnsEmptyWithoutCallingLlm() {
        assertTrue(extractor.extract("   ").isEmpty());
        assertTrue(extractor.extract(null).isEmpty());
        verifyNoInteractions(llmClient);
    }

    @Test
    void nonJsonOrEmptyLlmOutputReturnsEmpty() {
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("抱歉无法解析").build());
        assertTrue(extractor.extract("some resume").isEmpty());

        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("").build());
        assertTrue(extractor.extract("some resume").isEmpty());
    }

    @Test
    void llmExceptionIsSwallowedAsEmpty() {
        lenient().when(llmClient.chat(any(ChatRequest.class))).thenThrow(new IllegalStateException("llm down"));
        assertTrue(extractor.extract("some resume").isEmpty());
    }
}
