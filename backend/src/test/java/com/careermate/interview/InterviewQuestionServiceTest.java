package com.careermate.interview;

import com.careermate.agent.tool.rag.RagRetrieveResult;
import com.careermate.agent.tool.rag.RagRetrieveScene;
import com.careermate.agent.tool.rag.RagRetrievedChunk;
import com.careermate.interview.dto.JdAwareQuestionsVO;
import com.careermate.interview.service.InterviewQuestionService;
import com.careermate.knowledge.KnowledgeRetrievalService;
import com.careermate.llm.LlmClient;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.resume.ResumeContext;
import com.careermate.resume.ResumeContextProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewQuestionServiceTest {

    @Mock
    private KnowledgeRetrievalService knowledgeRetrievalService;
    @Mock
    private ResumeContextProvider resumeContextProvider;
    @Mock
    private LlmClient llmClient;

    private InterviewQuestionService service;

    @BeforeEach
    void setUp() {
        service = new InterviewQuestionService(
                knowledgeRetrievalService, resumeContextProvider, llmClient, new ObjectMapper());
    }

    @Test
    void nullJdDocIdReturnsFallback() {
        JdAwareQuestionsVO result = service.generateJdAwareQuestions(null, 1L);
        assertFalse(result.isDataAvailable());
        assertTrue(result.getQuestions().isEmpty());
    }

    @Test
    void jdNotFoundReturnsFallback() {
        when(knowledgeRetrievalService.retrieve(any()))
                .thenReturn(RagRetrieveResult.fallback("q", RagRetrieveScene.OPPORTUNITY, "EMPTY", 1));

        JdAwareQuestionsVO result = service.generateJdAwareQuestions(599L, 1L);

        assertFalse(result.isDataAvailable());
        assertEquals(599L, result.getJdDocId());
        assertTrue(result.getQuestions().isEmpty());
    }

    @Test
    void validGenerationParsesAndNormalizesQuestions() {
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(sampleJd("字节-后端-JD"));
        when(resumeContextProvider.getResumeContext(any()))
                .thenReturn(ResumeContext.builder().available(true).contextText("我的简历：Java 3年").build());
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"jdTitle":"字节-后端","questions":[
                  {"questionNo":1,"questionText":"讲讲缓存一致性","questionType":"技术",
                   "referencePoints":["旁路缓存"],"tag":"JD_FOCUSED","rationale":"JD重点"},
                  {"questionNo":9,"questionText":"分库分表怎么做","questionType":"技术","tag":"weird"},
                  {"questionNo":9,"questionText":"  ","tag":"HIT_RESUME"}
                ],"aiSummary":"围绕JD"}
                """).build());

        JdAwareQuestionsVO result = service.generateJdAwareQuestions(599L, 1L);

        assertTrue(result.isDataAvailable());
        assertEquals(599L, result.getJdDocId());
        assertEquals("字节-后端", result.getJdTitle());
        // 空题被剔除 → 2 道；题号重排 1,2
        assertEquals(2, result.getQuestions().size());
        assertEquals(1, result.getQuestions().get(0).getQuestionNo());
        assertEquals(2, result.getQuestions().get(1).getQuestionNo());
        // 非法 tag "weird" → JD_FOCUSED；referencePoints 补空
        assertEquals("JD_FOCUSED", result.getQuestions().get(1).getTag());
        assertNotNull(result.getQuestions().get(1).getReferencePoints());
    }

    @Test
    void noResumeStillGenerates() {
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(sampleJd("阿里-JD"));
        when(resumeContextProvider.getResumeContext(any()))
                .thenReturn(ResumeContext.builder().available(false).build());
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"jdTitle":"阿里","questions":[{"questionNo":1,"questionText":"系统设计","tag":"JD_FOCUSED"}],"aiSummary":"x"}
                """).build());

        JdAwareQuestionsVO result = service.generateJdAwareQuestions(600L, null);

        assertTrue(result.isDataAvailable());
        assertEquals(1, result.getQuestions().size());
    }

    @Test
    void jdTitleFallbackAndNullQuestionsHandled() {
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(sampleJdNoTitle());
        when(resumeContextProvider.getResumeContext(any()))
                .thenReturn(ResumeContext.builder().available(true).contextText("简历").build());
        // JSON 无 questions 字段 → normalizeQuestions 走 null 分支
        when(llmClient.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("{\"aiSummary\":\"暂无\"}").build());

        JdAwareQuestionsVO result = service.generateJdAwareQuestions(601L, 1L);

        assertTrue(result.isDataAvailable());
        assertEquals("该岗位", result.getJdTitle());
        assertNotNull(result.getQuestions());
        assertTrue(result.getQuestions().isEmpty());
    }

    @Test
    void invalidJsonReturnsFallback() {
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(sampleJd("字节-JD"));
        lenient().when(resumeContextProvider.getResumeContext(any()))
                .thenReturn(ResumeContext.builder().available(true).contextText("简历").build());
        when(llmClient.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("这不是 JSON").build());

        JdAwareQuestionsVO result = service.generateJdAwareQuestions(599L, 1L);
        assertFalse(result.isDataAvailable());
    }

    @Test
    void emptyLlmReturnsFallback() {
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(sampleJd("字节-JD"));
        lenient().when(resumeContextProvider.getResumeContext(any()))
                .thenReturn(ResumeContext.builder().available(true).contextText("简历").build());
        when(llmClient.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("").build());

        JdAwareQuestionsVO result = service.generateJdAwareQuestions(599L, 1L);
        assertFalse(result.isDataAvailable());
    }

    @Test
    void retrievalExceptionReturnsFallback() {
        when(knowledgeRetrievalService.retrieve(any())).thenThrow(new RuntimeException("rag down"));

        JdAwareQuestionsVO result = service.generateJdAwareQuestions(599L, 1L);
        assertFalse(result.isDataAvailable());
        assertEquals(599L, result.getJdDocId());
    }

    private static RagRetrieveResult sampleJd(String title) {
        return RagRetrieveResult.builder()
                .success(true)
                .query("岗位")
                .scene(RagRetrieveScene.OPPORTUNITY)
                .chunks(List.of(RagRetrievedChunk.builder()
                        .content("Java 后端 高并发 分布式 岗位职责")
                        .contentPreview("Java 后端")
                        .sourceTitle(title)
                        .fileName(title + ".md")
                        .score(0.9)
                        .build()))
                .latencyMs(5L)
                .build();
    }

    private static RagRetrieveResult sampleJdNoTitle() {
        return RagRetrieveResult.builder()
                .success(true)
                .query("岗位")
                .scene(RagRetrieveScene.OPPORTUNITY)
                .chunks(List.of(RagRetrievedChunk.builder()
                        .content("Java 后端 岗位职责内容")
                        .build()))
                .latencyMs(5L)
                .build();
    }
}
