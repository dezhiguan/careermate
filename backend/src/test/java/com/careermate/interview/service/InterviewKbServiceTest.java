package com.careermate.interview.service;

import com.careermate.agent.tool.rag.RagRetrieveRequest;
import com.careermate.agent.tool.rag.RagRetrieveScene;
import com.careermate.interview.dto.CompanyPrepVO;
import com.careermate.interview.dto.KbQuestionsVO;
import com.careermate.knowledge.KnowledgeRetrievalService;
import com.careermate.llm.LlmClient;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewKbServiceTest {

    private KnowledgeRetrievalService knowledgeRetrievalService;
    private LlmClient llmClient;
    private InterviewKbService service;

    @BeforeEach
    void setUp() {
        knowledgeRetrievalService = mock(KnowledgeRetrievalService.class);
        llmClient = mock(LlmClient.class);
        service = new InterviewKbService(knowledgeRetrievalService, llmClient, new ObjectMapper());
    }

    @Test
    void computeKbQuestionsParsesJsonAndFillsDefaults() {
        when(knowledgeRetrievalService.retrieveContextText(RagRetrieveScene.INTERVIEW, "Redis 面试题 考点", 20))
                .thenReturn("Redis 缓存穿透和缓存击穿");
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .content("""
                        前缀 {"aiSummary":"Redis 高频题","questions":[{"question":"什么是缓存穿透？","answer":"不存在的数据穿透到 DB","category":"Redis"}]} 后缀
                        """)
                .build());

        KbQuestionsVO result = service.computeKbQuestions("Redis");

        assertEquals("Redis", result.getQuery());
        assertEquals("Redis 高频题", result.getAiSummary());
        assertEquals(1, result.getQuestions().size());
        assertEquals("什么是缓存穿透？", result.getQuestions().get(0).getQuestion());
        assertEquals("FRESH", result.getMeta().state().name());
    }

    @Test
    void computeKbQuestionsFallsBackWhenContextOrJsonMissing() {
        when(knowledgeRetrievalService.retrieveContextText(RagRetrieveScene.INTERVIEW, "Java后端 面试题 考点", 20))
                .thenReturn("");

        KbQuestionsVO emptyContext = service.computeKbQuestions("");

        assertEquals("Java后端", emptyContext.getQuery());
        assertEquals("暂无相关面试资料", emptyContext.getAiSummary());
        assertTrue(emptyContext.getQuestions().isEmpty());
        assertEquals("DEGRADED", emptyContext.getMeta().state().name());

        when(knowledgeRetrievalService.retrieveContextText(RagRetrieveScene.INTERVIEW, "MySQL 面试题 考点", 20))
                .thenReturn("索引事务");
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("not json").build());

        KbQuestionsVO invalidJson = service.computeKbQuestions("MySQL");

        assertEquals("MySQL", invalidJson.getQuery());
        assertEquals("DEGRADED", invalidJson.getMeta().state().name());
    }

    @Test
    void computeKbQuestionsGeneratesFromGeneralKnowledgeWhenKbEmpty() {
        // 评审 P0-3：知识库对推荐关键词无命中时，改用 AI 通用知识现场生成，而非返回「暂无题目」。
        when(knowledgeRetrievalService.retrieveContextText(RagRetrieveScene.INTERVIEW, "Spring Boot 面试题 考点", 20))
                .thenReturn("");
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .content("{\"questions\":[{\"question\":\"Spring Boot 自动配置原理？\",\"answer\":\"基于 @EnableAutoConfiguration\",\"category\":\"技术\"}],\"aiSummary\":\"AI 依据通用知识生成，暂未匹配到知识库资料\"}")
                .build());

        KbQuestionsVO result = service.computeKbQuestions("Spring Boot");

        assertEquals("Spring Boot", result.getQuery());
        assertEquals(1, result.getQuestions().size());
        assertEquals("Spring Boot 自动配置原理？", result.getQuestions().get(0).getQuestion());
        assertFalse(result.getQuestions().isEmpty());
        assertEquals("FRESH", result.getMeta().state().name());
    }

    @Test
    void getKbQuestionsComputesSynchronouslyAndReturnsFreshNotLoadingWhenCacheEnabled() {
        // 评审 P0-3 根因修复：缓存开启且未命中时，getKbQuestions 应同步计算并返回 FRESH，
        // 而非返回 LOADING 后异步计算（异步线程失败→永不入缓存→前端永远转圈）。
        org.springframework.cache.CacheManager cacheManager =
                org.mockito.Mockito.mock(org.springframework.cache.CacheManager.class);
        org.springframework.cache.Cache cache = org.mockito.Mockito.mock(org.springframework.cache.Cache.class);
        when(cacheManager.getCache("interview:kb-questions")).thenReturn(cache);
        when(cache.get(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(KbQuestionsVO.class))).thenReturn(null); // 未命中
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<org.springframework.cache.CacheManager> provider =
                org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(cacheManager);
        org.springframework.context.ApplicationContext ctx =
                org.mockito.Mockito.mock(org.springframework.context.ApplicationContext.class);

        InterviewKbService cachedService =
                new InterviewKbService(knowledgeRetrievalService, llmClient, new ObjectMapper(), provider, ctx);
        when(ctx.getBean(InterviewKbService.class)).thenReturn(cachedService);
        when(knowledgeRetrievalService.retrieveContextText(RagRetrieveScene.INTERVIEW, "Spring Boot 面试题 考点", 20))
                .thenReturn("Spring Boot 自动配置、starter");
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .content("{\"questions\":[{\"question\":\"Spring Boot 自动配置原理？\",\"answer\":\"...\",\"category\":\"技术\"}],\"aiSummary\":\"重点\"}")
                .build());

        KbQuestionsVO result = cachedService.getKbQuestions("Spring Boot");

        assertEquals("FRESH", result.getMeta().state().name(), "应同步返回 FRESH，不再是 LOADING");
        assertEquals(1, result.getQuestions().size());
    }

    @Test
    void getCompanyPrepMergesCompanyAndInterviewContextThenParsesJson() {
        when(knowledgeRetrievalService.retrieveMergedContextText(any(), eq(4000)))
                .thenReturn("字节跳动后端面经");
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .content("""
                        {"companyName":"","interviewStyle":"偏项目深挖","techFocus":["Java","Redis"],"commonQuestions":["项目压测怎么做"],"aiSummary":"重点准备系统设计"}
                        """)
                .build());

        CompanyPrepVO result = service.getCompanyPrep(" 字节跳动 ");

        assertEquals("字节跳动", result.getCompanyName());
        assertEquals("偏项目深挖", result.getInterviewStyle());
        assertEquals(List.of("Java", "Redis"), result.getTechFocus());
        assertEquals(List.of("项目压测怎么做"), result.getCommonQuestions());
        verify(knowledgeRetrievalService).retrieveMergedContextText(any(List.class), eq(4000));
    }

    @Test
    void getCompanyPrepFallsBackForBlankEmptyContextAndException() {
        CompanyPrepVO blank = service.getCompanyPrep(" ");
        assertEquals("", blank.getCompanyName());
        assertEquals("请输入公司名称", blank.getAiSummary());

        when(knowledgeRetrievalService.retrieveMergedContextText(any(), eq(4000))).thenReturn("");
        CompanyPrepVO empty = service.getCompanyPrep("腾讯");
        assertEquals("腾讯", empty.getCompanyName());
        assertEquals("暂无该公司面试资料，请尝试其他公司名称", empty.getAiSummary());
        assertTrue(empty.getTechFocus().isEmpty());

        when(knowledgeRetrievalService.retrieveMergedContextText(any(), eq(4000))).thenThrow(new RuntimeException("rag down"));
        CompanyPrepVO failed = service.getCompanyPrep("阿里");
        assertEquals("阿里", failed.getCompanyName());
        assertFalse(failed.getAiSummary().isBlank());
    }
}
