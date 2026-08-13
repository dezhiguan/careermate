package com.careermate.market;

import com.careermate.agent.tool.rag.RagRetrieveRequest;
import com.careermate.agent.tool.rag.RagRetrieveResult;
import com.careermate.agent.tool.rag.RagRetrieveScene;
import com.careermate.agent.tool.rag.RagRetrievedChunk;
import com.careermate.agent.tool.rag.RagRetrieverChunkType;
import com.careermate.knowledge.KnowledgeRetrievalService;
import com.careermate.llm.LlmClient;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.market.dto.CompanyInsightVO;
import com.careermate.market.dto.ResumeGapVO;
import com.careermate.market.dto.SalaryInsightVO;
import com.careermate.market.dto.SkillTrendsVO;
import com.careermate.market.service.MarketIntelligenceService;
import com.careermate.resume.ResumeContext;
import com.careermate.resume.ResumeContextProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketIntelligenceServiceTest {

    private static final String FALLBACK_SUMMARY = "暂时无法获取市场数据，请稍后再试";
    private static final String NO_DATA = "暂无数据";

    @Mock
    private KnowledgeRetrievalService knowledgeRetrievalService;
    @Mock
    private LlmClient llmClient;
    @Mock
    private ResumeContextProvider resumeContextProvider;

    private MarketIntelligenceService service;

    @BeforeEach
    void setUp() {
        service = new MarketIntelligenceService(
                knowledgeRetrievalService,
                llmClient,
                resumeContextProvider,
                new ObjectMapper()
        );
        lenient().when(knowledgeRetrievalService.toMarketCitations(any())).thenAnswer(invocation -> {
            RagRetrieveResult result = invocation.getArgument(0);
            if (result == null || !result.isSuccess() || result.getChunks() == null) {
                return List.of();
            }
            return result.getChunks().stream().map(chunk -> {
                com.careermate.market.dto.MarketSourceCitationVO citation =
                        new com.careermate.market.dto.MarketSourceCitationVO();
                citation.setCitation(chunk.getCitation());
                citation.setContentPreview(chunk.getContentPreview());
                citation.setSourceTitle(chunk.getFileName());
                citation.setScore(chunk.getScore());
                citation.setChunkType(chunk.getChunkType() == null ? null : chunk.getChunkType().name());
                return citation;
            }).toList();
        });
        lenient().when(knowledgeRetrievalService.toSourceSummaries(any())).thenAnswer(invocation -> {
            RagRetrieveResult result = invocation.getArgument(0);
            if (result == null || !result.isSuccess() || result.getChunks() == null) {
                return List.of();
            }
            return result.getChunks().stream()
                    .map(chunk -> "[" + chunk.getCitation() + "] " + chunk.getContentPreview())
                    .toList();
        });
    }

    @Test
    void getSalaryInsightReturnsFallbackWhenRagEmpty() {
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(emptyResult(RagRetrieveScene.MARKET));

        SalaryInsightVO result = service.getSalaryInsight("Java后端", "广州", "3-5年");

        assertEquals(NO_DATA, result.getP25());
        assertEquals(NO_DATA, result.getP50());
        assertEquals(FALLBACK_SUMMARY, result.getAiSummary());
        assertTrue(result.getCitations().isEmpty());
        verify(llmClient, never()).chat(any());
    }

    @Test
    void getSalaryInsightParsesValidLlmJsonAndAttachesSafeCitations() {
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(sampleMarketResult());
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"p25":"22K","p50":"28K","p75":"32K","p90":"38K","trend":"上涨","aiSummary":"薪资稳步上涨"}
                """).build());

        SalaryInsightVO result = service.getSalaryInsight("Java后端", "广州", "3-5年");

        assertEquals("28K", result.getP50());
        assertEquals("上涨", result.getTrend());
        assertEquals("薪资稳步上涨", result.getAiSummary());
        assertEquals(1, result.getCitations().size());
        assertEquals("MARKET_REPORT@jd.md", result.getCitations().get(0).getCitation());
        assertEquals("月薪 25K-35K", result.getCitations().get(0).getContentPreview());
        assertFalse(result.getSourceSummaries().get(0).contains("完整敏感"));
    }

    @Test
    void getSalaryInsightReturnsFallbackWhenLlmReturnsNonJson() {
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(sampleMarketResult());
        when(llmClient.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("无法分析").build());

        SalaryInsightVO result = service.getSalaryInsight("Java后端", "广州", "3-5年");

        assertEquals(FALLBACK_SUMMARY, result.getAiSummary());
        assertEquals(NO_DATA, result.getP75());
    }

    @Test
    void getSkillTrendsParsesValidLlmJson() {
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(sampleMarketResult());
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"skills":[{"rank":1,"name":"Java","level":"高频","growth":"稳定"}],"aiSummary":"Java 仍是核心"}
                """).build());

        SkillTrendsVO result = service.getSkillTrends("Java后端");

        assertNotNull(result.getSkills());
        assertEquals(1, result.getSkills().size());
        assertEquals("Java", result.getSkills().get(0).getName());
        assertEquals("Java 仍是核心", result.getAiSummary());
        assertEquals(1, result.getCitations().size());
    }

    @Test
    void getSalaryInsightWithAnyYearsDropsYearsFromQueryAndDeclaresItInPrompt() {
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(sampleMarketResult());
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"p25":"18K","p50":"22K","p75":"26K","p90":"30K","trend":"平稳","aiSummary":"不限经验整体平稳"}
                """).build());

        service.getSalaryInsight("Java后端", "广州", "不限");

        ArgumentCaptor<RagRetrieveRequest> ragCaptor = ArgumentCaptor.forClass(RagRetrieveRequest.class);
        verify(knowledgeRetrievalService).retrieve(ragCaptor.capture());
        assertEquals("Java后端 广州 薪资 月薪", ragCaptor.getValue().getQuery());

        ArgumentCaptor<ChatRequest> chatCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmClient).chat(chatCaptor.capture());
        String prompt = chatCaptor.getValue().getMessages().get(1).getContent();
        // 口径插槽必须写成「全经验段」，而不是被补成某个具体年限区间
        assertTrue(
                prompt.contains("分析 Java后端 在 广州 地区、全经验段（不限工作年限）的薪资分布"),
                prompt.lines().findFirst().orElse(""));
    }

    @Test
    void getSalaryInsightWithoutYearsIsTreatedAsAny() {
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(sampleMarketResult());
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"p25":"18K","p50":"22K","p75":"26K","p90":"30K","trend":"平稳","aiSummary":"整体平稳"}
                """).build());

        service.getSalaryInsight("Java后端", "广州", null);

        ArgumentCaptor<RagRetrieveRequest> ragCaptor = ArgumentCaptor.forClass(RagRetrieveRequest.class);
        verify(knowledgeRetrievalService).retrieve(ragCaptor.capture());
        assertEquals("Java后端 广州 薪资 月薪", ragCaptor.getValue().getQuery());
    }

    @Test
    void getSalaryInsightWithConcreteYearsKeepsYearsInQuery() {
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(sampleMarketResult());
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"p25":"18K","p50":"22K","p75":"26K","p90":"30K","trend":"平稳","aiSummary":"平稳"}
                """).build());

        service.getSalaryInsight("Java后端", "广州", "3-5年");

        ArgumentCaptor<RagRetrieveRequest> ragCaptor = ArgumentCaptor.forClass(RagRetrieveRequest.class);
        verify(knowledgeRetrievalService).retrieve(ragCaptor.capture());
        assertEquals("Java后端 广州 3-5年 薪资 月薪", ragCaptor.getValue().getQuery());
    }

    @Test
    void skillHeatComesFromRealMentionCountsAndReordersRanks() {
        // Redis 出现 3 次、Java 2 次、MySQL 1 次；LLM 给的名次故意与词频相反
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(marketResultWithContent(
                "Java 工程师 熟悉 Redis 与 MySQL；Redis 集群经验优先；缓存用 Redis；Java 8 以上"));
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"skills":[{"rank":1,"name":"MySQL","level":"高频","growth":"稳定"},
                           {"rank":2,"name":"Java","level":"高频","growth":"稳定"},
                           {"rank":3,"name":"Redis","level":"中频","growth":"上涨"}],
                 "aiSummary":"缓存需求强"}
                """).build());

        SkillTrendsVO result = service.getSkillTrends("Java后端");

        assertEquals(3, result.getSkills().size());
        SkillTrendsVO.SkillItem top = result.getSkills().get(0);
        assertEquals("Redis", top.getName());
        assertEquals(1, top.getRank());
        assertEquals(3, top.getMentions());
        assertEquals(100, top.getHeat());

        SkillTrendsVO.SkillItem second = result.getSkills().get(1);
        assertEquals("Java", second.getName());
        assertEquals(2, second.getMentions());
        assertEquals(67, second.getHeat());

        SkillTrendsVO.SkillItem third = result.getSkills().get(2);
        assertEquals("MySQL", third.getName());
        assertEquals(1, third.getMentions());
        assertEquals(33, third.getHeat());
    }

    @Test
    void salaryQuantilesAreComputedFromSamplesNotEstimatedByLlm() {
        // 语料里 10 条明确薪资，分位应由后端算出；LLM 只被要求给 trend/aiSummary
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(marketResultWithContent(
                "A 10K B 12K C 14K D 16K E 18K F 20K G 22K H 24K I 26K J 28K"));
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"trend":"平稳","aiSummary":"广州 Java 后端全经验段中位 19K"}
                """).build());

        SalaryInsightVO result = service.getSalaryInsight("Java后端", "广州", "不限");

        assertEquals("15K", result.getP25());   // 插值落在 14K 与 16K 之间 → 14.5K
        assertEquals("19K", result.getP50());
        assertEquals("24K", result.getP75());
        assertEquals("26K", result.getP90());
        assertEquals("平稳", result.getTrend());

        ArgumentCaptor<ChatRequest> chatCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmClient).chat(chatCaptor.capture());
        String prompt = chatCaptor.getValue().getMessages().get(1).getContent();
        assertTrue(prompt.contains("P50=19K"), "分位应作为既定事实喂给 LLM");
        assertTrue(prompt.contains("不得修改"), prompt.lines().findFirst().orElse(""));
    }

    @Test
    void computedQuantilesSurviveLlmNarrativeFailure() {
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(marketResultWithContent(
                "A 10K B 12K C 14K D 16K E 18K F 20K G 22K H 24K I 26K J 28K"));
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("网络异常").build());

        SalaryInsightVO result = service.getSalaryInsight("Java后端", "广州", "不限");

        // 文案拿不到不影响分位——数字本身是确定的，不该整体降级成「暂无数据」
        assertEquals("19K", result.getP50());
        assertEquals(NO_DATA, result.getTrend());
    }

    @Test
    void estimatedQuantileOutsideObservedRangeIsClampedBack() {
        // 样本不足 8 条走 LLM 估算，但 80K 远超原文观测上限 30K，属编造
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(
                marketResultWithContent("A 20K B 25K C 30K"));
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"p25":"22K","p50":"26K","p75":"80K","p90":"90K","trend":"上涨","aiSummary":"—"}
                """).build());

        SalaryInsightVO result = service.getSalaryInsight("Java后端", "广州", "不限");

        assertEquals("22K", result.getP25());
        assertEquals("26K", result.getP50());
        assertEquals("30K", result.getP75());
        assertEquals("30K", result.getP90());
    }

    @Test
    void companyInsightOnlyReadsChunksMentioningThatCompany() {
        // 向量检索把别家公司的 JD 也召回了，必须先按公司过滤再交给 LLM
        RagRetrieveResult mixed = RagRetrieveResult.builder()
                .success(true)
                .query("华为")
                .scene(RagRetrieveScene.COMPANY)
                .chunks(List.of(
                        RagRetrievedChunk.builder().content("华为 在招 通信软件 岗位 Java")
                                .contentPreview("华为").citation("COMPANY@a.md")
                                .chunkType(RagRetrieverChunkType.COMPANY).fileName("a.md").score(0.9).build(),
                        RagRetrievedChunk.builder().content("腾讯 在招 全栈工程师 Vue")
                                .contentPreview("腾讯").citation("COMPANY@b.md")
                                .chunkType(RagRetrieverChunkType.COMPANY).fileName("b.md").score(0.88).build()))
                .latencyMs(5L)
                .build();
        when(knowledgeRetrievalService.retrieveMerged(any())).thenReturn(mixed);
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"companyName":"华为","scale":"大厂","stage":"未上市","techStack":["Java"],
                 "currentJds":[],"aiSummary":"—"}
                """).build());

        service.getCompanyInsight("华为科技有限公司");

        ArgumentCaptor<ChatRequest> chatCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmClient).chat(chatCaptor.capture());
        String prompt = chatCaptor.getValue().getMessages().get(1).getContent();
        assertTrue(prompt.contains("华为"), prompt);
        assertFalse(prompt.contains("腾讯"), "别家公司的 JD 不得进入本公司情报的上下文");
    }

    @Test
    void companyInsightFallsBackWhenNoChunkMentionsTheCompany() {
        when(knowledgeRetrievalService.retrieveMerged(any())).thenReturn(
                companyResultWithContent("腾讯 在招 全栈工程师"));

        CompanyInsightVO result = service.getCompanyInsight("某不存在的公司");

        assertEquals(NO_DATA, result.getScale());
        assertTrue(result.getTechStack().isEmpty());
        verify(llmClient, never()).chat(any());
    }

    @Test
    void companyScaleDropsStageDuplicatedInIt() {
        when(knowledgeRetrievalService.retrieveMerged(any())).thenReturn(sampleCompanyResult());
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"companyName":"腾讯","scale":"大厂 / 上市公司","stage":"上市",
                 "techStack":["Java"],"currentJds":[],"aiSummary":"—"}
                """).build());

        CompanyInsightVO result = service.getCompanyInsight("腾讯");

        assertEquals("大厂", result.getScale());
        assertEquals("上市", result.getStage());
    }

    @Test
    void skillTrendsRetrievesFromJdKbNotSalaryReportKb() {
        // 薪资行情库正文是「公司名 + 薪资表」，查它会把企业名当成技能；技术栈只在岗位 JD 库里
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(
                marketResultWithContent("熟悉 Java 与 Spring Boot"));
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"skills":[{"rank":1,"name":"Java","level":"高频","growth":"稳定"}],"aiSummary":"—"}
                """).build());

        service.getSkillTrends("广州", "Java后端");

        ArgumentCaptor<RagRetrieveRequest> captor = ArgumentCaptor.forClass(RagRetrieveRequest.class);
        verify(knowledgeRetrievalService).retrieve(captor.capture());
        assertEquals(RagRetrieveScene.OPPORTUNITY, captor.getValue().getScene());
    }

    @Test
    void skillHeatDropsSkillsThatNeverAppearInContext() {
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(
                marketResultWithContent("熟悉 Java 与 Spring Boot 微服务"));
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"skills":[{"rank":1,"name":"Java","level":"高频","growth":"稳定"},
                           {"rank":2,"name":"Kubernetes","level":"高频","growth":"快涨"}],
                 "aiSummary":"后端基本盘"}
                """).build());

        SkillTrendsVO result = service.getSkillTrends("Java后端");

        assertEquals(1, result.getSkills().size());
        assertEquals("Java", result.getSkills().get(0).getName());
    }

    @Test
    void skillHeatDoesNotCountSubstringMatches() {
        // "JavaScript" 不能被算成 "Java" 的一次提及
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(
                marketResultWithContent("需要 JavaScript 与 TypeScript；了解 Java 者优先"));
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"skills":[{"rank":1,"name":"JavaScript","level":"高频","growth":"稳定"},
                           {"rank":2,"name":"Java","level":"中频","growth":"稳定"}],
                 "aiSummary":"前端为主"}
                """).build());

        SkillTrendsVO result = service.getSkillTrends("前端开发");

        assertEquals(2, result.getSkills().size());
        assertEquals(1, result.getSkills().stream()
                .filter(s -> "Java".equals(s.getName())).findFirst().orElseThrow().getMentions());
    }

    @Test
    void skillHeatIsNullWhenNoSkillMatchesContext() {
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(
                marketResultWithContent("岗位职责描述，未列出具体技术栈"));
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"skills":[{"rank":1,"name":"Java","level":"高频","growth":"稳定"}],"aiSummary":"—"}
                """).build());

        SkillTrendsVO result = service.getSkillTrends("Java后端");

        assertEquals(1, result.getSkills().size());
        assertNull(result.getSkills().get(0).getHeat());
        assertNull(result.getSkills().get(0).getMentions());
    }

    @Test
    void getSkillTrendsReturnsFallbackWhenLlmThrows() {
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(sampleMarketResult());
        when(llmClient.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("timeout"));

        SkillTrendsVO result = service.getSkillTrends("Java后端");

        assertTrue(result.getSkills().isEmpty());
        assertEquals(FALLBACK_SUMMARY, result.getAiSummary());
    }

    @Test
    void getResumeGapReturnsEmptyWhenNoResume() {
        when(resumeContextProvider.getResumeContext(1L)).thenReturn(
                ResumeContext.builder().available(false).build()
        );

        ResumeGapVO result = service.getResumeGap(1L);

        assertEquals(0, result.getMatchScore());
        assertTrue(result.getHasSkills().isEmpty());
        assertTrue(result.getMissingSkills().isEmpty());
        assertEquals("", result.getAiSummary());
        verify(knowledgeRetrievalService, never()).retrieve(any());
    }

    @Test
    void getResumeGapComputesSynchronouslyReturnsTerminalStateNotLoadingWhenCacheEnabled() {
        // 修复验证：缓存开启且未命中时，getResumeGap 应同步计算并返回终态（非 LOADING），
        // 不再走「异步刷新+返回 LOADING」导致永远转圈。
        org.springframework.cache.CacheManager cacheManager =
                org.mockito.Mockito.mock(org.springframework.cache.CacheManager.class);
        org.springframework.cache.Cache cache = org.mockito.Mockito.mock(org.springframework.cache.Cache.class);
        org.mockito.Mockito.when(cacheManager.getCache("market:resume-gap")).thenReturn(cache);
        org.mockito.Mockito.when(cache.get(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(ResumeGapVO.class))).thenReturn(null); // 未命中
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<org.springframework.cache.CacheManager> provider =
                org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(cacheManager);
        org.springframework.context.ApplicationContext ctx =
                org.mockito.Mockito.mock(org.springframework.context.ApplicationContext.class);

        MarketIntelligenceService cachedService = new MarketIntelligenceService(
                knowledgeRetrievalService, llmClient, resumeContextProvider, new ObjectMapper(), provider, ctx);
        when(resumeContextProvider.getResumeContext(1L)).thenReturn(ResumeContext.builder().available(false).build());

        ResumeGapVO result = cachedService.getResumeGap(1L);

        assertNotNull(result.getMeta());
        assertNotEquals("LOADING", result.getMeta().state().name(), "应同步返回终态，不再永远 LOADING");
    }

    @Test
    void getResumeGapReturnsFallbackWhenRagEmpty() {
        when(resumeContextProvider.getResumeContext(1L)).thenReturn(
                ResumeContext.builder()
                        .available(true)
                        .title("Java后端工程师")
                        .content("熟悉 Java Spring")
                        .build()
        );
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(emptyResult(RagRetrieveScene.OPPORTUNITY));

        ResumeGapVO result = service.getResumeGap(1L);

        assertEquals(0, result.getMatchScore());
        assertEquals(FALLBACK_SUMMARY, result.getAiSummary());
    }

    @Test
    void getResumeGapParsesValidLlmJson() {
        when(resumeContextProvider.getResumeContext(1L)).thenReturn(
                ResumeContext.builder()
                        .available(true)
                        .title("Java后端工程师")
                        .content("熟悉 Java Spring Redis")
                        .build()
        );
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(sampleOpportunityResult());
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"hasSkills":["Java","Spring"],"missingSkills":["K8s"],"matchScore":72,"topSuggestion":"补 K8s","aiSummary":"整体匹配良好"}
                """).build());

        ResumeGapVO result = service.getResumeGap(1L);

        assertEquals(72, result.getMatchScore());
        assertEquals("K8s", result.getMissingSkills().get(0));
        assertEquals("补 K8s", result.getTopSuggestion());
    }

    @Test
    void resumeGapDropsHasSkillThatJdNeverRequires() {
        // 简历有 Java 和 Redis，但 JD 只要求 Redis/K8s：把 Java 记进「已具备且岗位要求」是虚记功劳
        when(resumeContextProvider.getResumeContext(1L)).thenReturn(
                ResumeContext.builder().available(true).title("Java后端工程师")
                        .content("熟悉 Java Redis 与 MySQL").build());
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(sampleOpportunityResult());
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"hasSkills":["Java","Redis"],"missingSkills":["K8s"],"matchScore":72,
                 "topSuggestion":"补 K8s","aiSummary":"整体匹配良好"}
                """).build());

        ResumeGapVO result = service.getResumeGap(1L);

        assertEquals(List.of("Redis"), result.getHasSkills());
        assertEquals(List.of("K8s"), result.getMissingSkills());
        // matchScore 是 LLM 对经历与深度的综合判断，不因技能词剔除而重算
        assertEquals(72, result.getMatchScore());
    }

    @Test
    void resumeGapDropsMissingSkillTheResumeActuallyHas() {
        // 简历里明明写了 Redis，却被列为「缺失技能」——凭空造出的差距会误导改简历
        when(resumeContextProvider.getResumeContext(1L)).thenReturn(
                ResumeContext.builder().available(true).title("Java后端工程师")
                        .content("熟悉 Redis 集群与缓存设计").build());
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(sampleOpportunityResult());
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"hasSkills":[],"missingSkills":["Redis","K8s"],"matchScore":60,
                 "topSuggestion":"补 K8s","aiSummary":"—"}
                """).build());

        ResumeGapVO result = service.getResumeGap(1L);

        assertEquals(List.of("K8s"), result.getMissingSkills());
    }

    @Test
    void companyInsightDropsTechStackAbsentFromJd() {
        when(knowledgeRetrievalService.retrieveMerged(any())).thenReturn(sampleCompanyResult());
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"companyName":"腾讯","scale":"大厂","stage":"上市",
                 "techStack":["Java","Kubernetes"],"currentJds":[],"aiSummary":"互联网大厂"}
                """).build());

        CompanyInsightVO result = service.getCompanyInsight("腾讯");

        assertEquals(List.of("Java"), result.getTechStack());
    }

    @Test
    void companyInsightKeepsNormalizedJobTitleButDropsFabricatedOne() {
        // 原文写「资深java研发工程师」，LLM 规范化成「Java后端工程师」应保留（特征词 java 命中）；
        // 「算法工程师」在原文里没有任何特征词支撑，判为编造
        when(knowledgeRetrievalService.retrieveMerged(any())).thenReturn(
                companyResultWithContent("某公司 在招 资深java研发工程师 与 前端开发"));
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"companyName":"某公司","scale":"中型企业","stage":"未知","techStack":[],
                 "currentJds":["Java后端工程师","算法工程师"],"aiSummary":"—"}
                """).build());

        CompanyInsightVO result = service.getCompanyInsight("某公司");

        assertEquals(List.of("Java后端工程师"), result.getCurrentJds());
    }

    @Test
    void getCompanyInsightReturnsFallbackWhenCompanyBlank() {
        CompanyInsightVO result = service.getCompanyInsight("  ");

        assertEquals(NO_DATA, result.getCompanyName());
        assertEquals(FALLBACK_SUMMARY, result.getAiSummary());
        verify(knowledgeRetrievalService, never()).retrieveMerged(any());
    }

    @Test
    void getCompanyInsightReturnsFallbackWhenRagEmpty() {
        when(knowledgeRetrievalService.retrieveMerged(any())).thenReturn(emptyResult(RagRetrieveScene.COMPANY));

        CompanyInsightVO result = service.getCompanyInsight("腾讯");

        assertEquals("腾讯", result.getCompanyName());
        assertEquals(FALLBACK_SUMMARY, result.getAiSummary());
        assertTrue(result.getTechStack().isEmpty());
    }

    @Test
    void getCompanyInsightParsesValidLlmJsonAndAttachesCompanyCitations() {
        when(knowledgeRetrievalService.retrieveMerged(any())).thenReturn(sampleCompanyResult());
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"companyName":"","scale":"大厂","stage":"上市","techStack":["Java"],"currentJds":["Java后端"],"aiSummary":"互联网大厂"}
                """).build());

        CompanyInsightVO result = service.getCompanyInsight("腾讯");

        assertEquals("腾讯", result.getCompanyName());
        assertEquals("大厂", result.getScale());
        assertEquals("Java", result.getTechStack().get(0));
        assertEquals("Java后端", result.getCurrentJds().get(0));
        assertEquals(RagRetrieverChunkType.COMPANY.name(), result.getCitations().get(0).getChunkType());
        verify(knowledgeRetrievalService).retrieveMerged(any());
    }

    @Test
    void getCompanyInsightReturnsFallbackWhenLlmReturnsInvalidJson() {
        when(knowledgeRetrievalService.retrieveMerged(any())).thenReturn(sampleCompanyResult());
        when(llmClient.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("not json").build());

        CompanyInsightVO result = service.getCompanyInsight("腾讯");

        assertEquals("腾讯", result.getCompanyName());
        assertEquals(FALLBACK_SUMMARY, result.getAiSummary());
    }

    private static RagRetrieveResult emptyResult(RagRetrieveScene scene) {
        return RagRetrieveResult.fallback("q", scene, KnowledgeRetrievalService.ERROR_EMPTY_RESULTS, 1);
    }

    private static RagRetrieveResult sampleMarketResult() {
        return RagRetrieveResult.builder()
                .success(true)
                .query("薪资")
                .scene(RagRetrieveScene.MARKET)
                .chunks(List.of(RagRetrievedChunk.builder()
                        .content("月薪 25K-35K 完整敏感段落不应出现在 citations")
                        .contentPreview("月薪 25K-35K")
                        .citation("MARKET_REPORT@jd.md")
                        .chunkType(RagRetrieverChunkType.MARKET_REPORT)
                        .fileName("jd.md")
                        .score(0.9)
                        .build()))
                .latencyMs(5L)
                .build();
    }

    private static RagRetrieveResult marketResultWithContent(String content) {
        return RagRetrieveResult.builder()
                .success(true)
                .query("技能")
                .scene(RagRetrieveScene.MARKET)
                .chunks(List.of(RagRetrievedChunk.builder()
                        .content(content)
                        .contentPreview(content)
                        .citation("MARKET_REPORT@jd.md")
                        .chunkType(RagRetrieverChunkType.MARKET_REPORT)
                        .fileName("jd.md")
                        .score(0.9)
                        .build()))
                .latencyMs(5L)
                .build();
    }

    private static RagRetrieveResult sampleOpportunityResult() {
        return RagRetrieveResult.builder()
                .success(true)
                .query("岗位要求")
                .scene(RagRetrieveScene.OPPORTUNITY)
                .chunks(List.of(RagRetrievedChunk.builder()
                        .content("要求 Redis K8s")
                        .contentPreview("要求 Redis K8s")
                        .citation("JD@jd.md")
                        .chunkType(RagRetrieverChunkType.JD)
                        .fileName("jd.md")
                        .score(0.8)
                        .build()))
                .latencyMs(5L)
                .build();
    }

    private static RagRetrieveResult companyResultWithContent(String content) {
        return RagRetrieveResult.builder()
                .success(true)
                .query("公司")
                .scene(RagRetrieveScene.COMPANY)
                .chunks(List.of(RagRetrievedChunk.builder()
                        .content(content)
                        .contentPreview(content)
                        .citation("COMPANY@company.md")
                        .chunkType(RagRetrieverChunkType.COMPANY)
                        .fileName("company.md")
                        .score(0.88)
                        .build()))
                .latencyMs(5L)
                .build();
    }

    private static RagRetrieveResult sampleCompanyResult() {
        return RagRetrieveResult.builder()
                .success(true)
                .query("腾讯")
                .scene(RagRetrieveScene.COMPANY)
                .chunks(List.of(RagRetrievedChunk.builder()
                        .content("腾讯 大厂 Java")
                        .contentPreview("腾讯 大厂 Java")
                        .citation("COMPANY@company.md")
                        .chunkType(RagRetrieverChunkType.COMPANY)
                        .fileName("company.md")
                        .score(0.88)
                        .build()))
                .latencyMs(5L)
                .build();
    }
}
