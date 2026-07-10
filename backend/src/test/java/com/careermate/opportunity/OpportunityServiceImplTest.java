package com.careermate.opportunity;

import com.careermate.common.api.PageResult;
import com.careermate.common.api.CacheMeta;
import com.careermate.common.exception.BizException;
import com.careermate.model.entity.ResumeEntity;
import com.careermate.opportunity.dto.OpportunityDetailVO;
import com.careermate.opportunity.dto.OpportunityListItemVO;
import com.careermate.opportunity.dto.OpportunityListRequest;
import com.careermate.opportunity.dto.OpportunityPrepareResponse;
import com.careermate.model.entity.AgentMessageEntity;
import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.opportunity.service.impl.OpportunityServiceImpl;
import com.careermate.profile.service.CareerProfileService;
import com.careermate.workspace.support.WorkspaceSessionRepository;
import com.careermate.profile.dto.CareerProfileResponse;
import com.careermate.knowledge.KnowledgeRetrievalService;
import com.careermate.ragforge.RagForgeChunk;
import com.careermate.ragforge.RagForgeClient;
import com.careermate.ragforge.RagForgeProperties;
import com.careermate.resume.service.ResumeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpportunityServiceImplTest {

    private static final String SAMPLE_JD = """
            # 【JD】星天科技 | 算法工程师 |  | 北京
            **公司**:星天科技
            **城市**:北京
            **经验**:1-3年
            **学历**:硕士
            **规模**:100-499人
            **技术标签**:Java, Redis, 算法设计
            ## 职位描述
            负责信号处理算法
            """;

    @Mock
    private RagForgeClient ragForgeClient;
    @Mock
    private ResumeService resumeService;
    @Mock
    private CareerProfileService careerProfileService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private WorkspaceSessionRepository workspaceSessionRepository;

    private OpportunityServiceImpl service;
    private ObjectMapper objectMapper;
    private KnowledgeRetrievalService knowledgeRetrievalService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        RagForgeProperties properties = new RagForgeProperties();
        properties.setEnabled(true);
        properties.setJdKbId("16");
        knowledgeRetrievalService = new KnowledgeRetrievalService(ragForgeClient, properties);
        service = new OpportunityServiceImpl(
                knowledgeRetrievalService,
                ragForgeClient,
                resumeService,
                careerProfileService,
                workspaceSessionRepository,
                objectMapper,
                redisTemplate
        );
    }

    @Test
    void listKeywordEmptyNoProfileUsesDefaultQuery() {
        when(careerProfileService.getProfile(1L)).thenReturn(emptyProfile());
        when(ragForgeClient.searchJd("Java 后端", 30)).thenReturn(List.of());

        service.list(1L, new OpportunityListRequest(null, null, null, null, 1, 10));

        verify(ragForgeClient).searchJd("Java 后端", 30);
    }

    @Test
    void listKeywordEmptyWithProfileUsesRoleAndCity() {
        when(careerProfileService.getProfile(2L)).thenReturn(CareerProfileResponse.builder()
                .targetRole("Java 后端")
                .targetCity("北京")
                .build());
        when(ragForgeClient.searchJd("Java 后端 北京", 30)).thenReturn(List.of());

        service.list(2L, new OpportunityListRequest(null, null, null, null, 1, 10));

        verify(ragForgeClient).searchJd("Java 后端 北京", 30);
    }

    @Test
    void listKeywordPassedThrough() {
        when(ragForgeClient.searchJd("Redis", 30)).thenReturn(List.of());

        service.list(1L, new OpportunityListRequest("Redis", null, null, null, 1, 10));

        verify(ragForgeClient).searchJd("Redis", 30);
        verify(careerProfileService, times(0)).getProfile(any());
    }

    @Test
    void listRagForgeEmptyReturnsZeroTotal() {
        when(ragForgeClient.searchJd(anyString(), eq(30))).thenReturn(List.of());

        PageResult<OpportunityListItemVO> result =
                service.list(1L, new OpportunityListRequest("test", null, null, null, 1, 10));

        assertEquals(0, result.total());
        assertTrue(result.items().isEmpty());
        assertEquals(CacheMeta.State.EMPTY, result.meta().state());
    }

    @Test
    void listRagForgeFailureReturnsDegradedNotLoading() {
        when(ragForgeClient.searchJd(anyString(), eq(30))).thenThrow(new IllegalStateException("rag down"));

        PageResult<OpportunityListItemVO> result =
                service.list(1L, new OpportunityListRequest("test", null, null, null, 1, 10));

        assertEquals(0, result.total());
        assertTrue(result.items().isEmpty());
        assertEquals(CacheMeta.State.DEGRADED, result.meta().state());
    }

    @Test
    void listMultiChunkAggregation() {
        when(ragForgeClient.searchJd("Java", 30)).thenReturn(List.of(
                new RagForgeChunk(2L, 100L, "f.md", "尾部", "JD", 0.5),
                new RagForgeChunk(1L, 100L, "f.md", SAMPLE_JD, "JD", 0.8)
        ));
        when(resumeService.getDefaultActiveResume(1L)).thenReturn(Optional.empty());

        PageResult<OpportunityListItemVO> result =
                service.list(1L, new OpportunityListRequest("Java", null, null, null, 1, 10));

        assertEquals(1, result.total());
        assertEquals("星天科技", result.items().get(0).company());
        assertEquals("算法工程师", result.items().get(0).title());
    }

    @Test
    void listWithResumeMatchScoreSortedByMatchScoreDesc() {
        when(ragForgeClient.searchJd("Java", 30)).thenReturn(List.of(
                chunk(1L, 1L, SAMPLE_JD.replace("算法设计", "Python"), 0.9),
                chunk(2L, 2L, SAMPLE_JD, 0.5)
        ));
        ResumeEntity resume = new ResumeEntity();
        resume.setContent("熟悉 Java, Redis, Spring Boot");
        when(resumeService.getDefaultActiveResume(1L)).thenReturn(Optional.of(resume));

        PageResult<OpportunityListItemVO> result =
                service.list(1L, new OpportunityListRequest("Java", null, null, null, 1, 10));

        assertTrue(result.hasResume());
        assertEquals("MATCH", result.sortStrategy());
        assertNotNull(result.items().get(0).matchScore());
        assertTrue(result.items().get(0).matchScore() >= result.items().get(1).matchScore());
    }

    @Test
    void listWithoutResumeNullMatchScoreSortedByRagScoreDesc() {
        when(ragForgeClient.searchJd("Java", 30)).thenReturn(List.of(
                chunk(1L, 1L, SAMPLE_JD, 0.3),
                chunk(2L, 2L, SAMPLE_JD, 0.9)
        ));
        when(resumeService.getDefaultActiveResume(1L)).thenReturn(Optional.empty());

        PageResult<OpportunityListItemVO> result =
                service.list(1L, new OpportunityListRequest("Java", null, null, null, 1, 10));

        assertFalse(result.hasResume());
        assertEquals("LATEST", result.sortStrategy());
        assertNull(result.items().get(0).matchScore());
        assertEquals("UNKNOWN", result.items().get(0).matchTier());
        assertEquals(0.9, result.items().get(0).ragScore());
    }

    @Test
    void listDemoModeWithoutResumeUsesDefaultDemoQueryAndMarksItems() {
        when(ragForgeClient.searchJd("广州 Java 3-5年", 30)).thenReturn(List.of(
                chunk(1L, 1L, SAMPLE_JD, 0.8)
        ));
        when(resumeService.getDefaultActiveResume(1L)).thenReturn(Optional.empty());

        PageResult<OpportunityListItemVO> result =
                service.list(1L, new OpportunityListRequest(null, null, null, "demo", 1, 10));

        assertFalse(result.hasResume());
        assertEquals(1, result.total());
        assertTrue(result.items().get(0).isDemo());
        assertNull(result.items().get(0).matchScore());
        verify(ragForgeClient).searchJd("广州 Java 3-5年", 30);
    }

    @Test
    void listDemoModeUsesRequestKeywordCityAndPosition() {
        String pythonJd = SAMPLE_JD
                .replace("算法工程师", "Python 后端工程师")
                .replace("北京", "深圳")
                .replace("Java, Redis, 算法设计", "Python, Django, Redis");
        when(ragForgeClient.searchJd("Python 深圳 后端 3-5年", 30)).thenReturn(List.of(
                chunk(1L, 1L, pythonJd, 0.8)
        ));
        when(resumeService.getDefaultActiveResume(1L)).thenReturn(Optional.empty());

        PageResult<OpportunityListItemVO> result =
                service.list(1L, new OpportunityListRequest(" Python ", "深圳", "后端", "demo", 1, 10));

        assertFalse(result.hasResume());
        assertEquals(1, result.total());
        assertTrue(result.items().get(0).isDemo());
        assertEquals("Python 后端工程师", result.items().get(0).title());
        assertTrue(result.items().get(0).skills().contains("Python"));
        assertNull(result.items().get(0).matchScore());
        verify(ragForgeClient).searchJd("Python 深圳 后端 3-5年", 30);
    }

    @Test
    void listDoesNotUseCachedOpportunityList() {
        when(ragForgeClient.searchJd("cached-query", 30)).thenReturn(List.of(chunk(1L, 1L, SAMPLE_JD, 0.8)));

        PageResult<OpportunityListItemVO> result =
                service.list(1L, new OpportunityListRequest("cached-query", null, null, null, 1, 10));

        assertEquals(1, result.total());
        verify(redisTemplate, times(0)).opsForValue();
        verify(ragForgeClient).searchJd("cached-query", 30);
    }

    @Test
    void listRedisUnavailableStillReturnsResult() {
        OpportunityServiceImpl degraded = new OpportunityServiceImpl(
                knowledgeRetrievalService,
                ragForgeClient,
                resumeService,
                careerProfileService,
                workspaceSessionRepository,
                objectMapper,
                null
        );
        when(ragForgeClient.searchJd("Java", 30)).thenReturn(List.of(chunk(1L, 1L, SAMPLE_JD, 0.7)));
        when(resumeService.getDefaultActiveResume(1L)).thenReturn(Optional.empty());

        PageResult<OpportunityListItemVO> result =
                degraded.list(1L, new OpportunityListRequest("Java", null, null, null, 1, 10));

        assertEquals(1, result.total());
    }

    @Test
    void detailInvalidJdIdThrowsBizException() {
        assertThrows(BizException.class, () -> service.detail(1L, "bad-id"));
    }

    @Test
    void prepareCreatesJdPrepSessionAndWelcomeCard() {
        when(workspaceSessionRepository.findActiveJdPrepSession(1L, "doc-55")).thenReturn(null);
        when(ragForgeClient.fetchDocumentChunks(55L)).thenReturn(List.of(
                chunk(1L, 55L, SAMPLE_JD, 0.8)
        ));
        AgentSessionEntity created = new AgentSessionEntity();
        created.setSessionId("WS-testsession1");
        when(workspaceSessionRepository.createJdPrepSession(eq(1L), eq("doc-55"), anyString(), anyString()))
                .thenReturn(created);
        when(workspaceSessionRepository.appendMessage(eq(1L), eq(created), eq("assistant"), anyString(), eq("CARD"), anyString(), eq(1)))
                .thenReturn(new AgentMessageEntity());

        OpportunityPrepareResponse response = service.prepare(1L, "doc-55");

        assertEquals("WS-testsession1", response.workspaceId());
        assertEquals("/chat/WS-testsession1", response.redirectPath());
        ArgumentCaptor<String> metadataCaptor = ArgumentCaptor.forClass(String.class);
        verify(workspaceSessionRepository).appendMessage(
                eq(1L), eq(created), eq("assistant"), anyString(), eq("CARD"), metadataCaptor.capture(), eq(1)
        );
        assertTrue(metadataCaptor.getValue().contains("OFFER_GENERATE_RESUME"));
    }

    @Test
    void prepareIdempotentReturnsExistingSession() {
        AgentSessionEntity existing = new AgentSessionEntity();
        existing.setSessionId("WS-existing1234");
        when(workspaceSessionRepository.findActiveJdPrepSession(1L, "doc-55")).thenReturn(existing);

        OpportunityPrepareResponse response = service.prepare(1L, "doc-55");

        assertEquals("WS-existing1234", response.workspaceId());
        verify(workspaceSessionRepository, times(0)).createJdPrepSession(any(), any(), any(), any());
    }

    @Test
    void prepareJdNotFoundThrowsBizException() {
        when(workspaceSessionRepository.findActiveJdPrepSession(1L, "doc-999")).thenReturn(null);
        when(ragForgeClient.fetchDocumentChunks(999L)).thenReturn(List.of());
        when(ragForgeClient.searchJd(anyString(), eq(50))).thenReturn(List.of());

        assertThrows(BizException.class, () -> service.prepare(1L, "doc-999"));
    }

    // 修复回归：documents/{id}/chunks 端点对 API-Key 不开放（取正文空），
    // 应改走 /search + docIds 过滤（searchJdByDocId）取到 JD，prepare 仍成功——不再误报"不存在或已下架"。
    @Test
    void prepareFallsBackToSearchByDocIdWhenDocChunksUnavailable() {
        when(workspaceSessionRepository.findActiveJdPrepSession(1L, "doc-83428")).thenReturn(null);
        when(ragForgeClient.searchJdByDocId(83428L, 50)).thenReturn(List.of(
                chunk(1L, 83428L, SAMPLE_JD, 0.9)
        ));
        AgentSessionEntity created = new AgentSessionEntity();
        created.setSessionId("WS-fesession001");
        when(workspaceSessionRepository.createJdPrepSession(eq(1L), eq("doc-83428"), anyString(), anyString()))
                .thenReturn(created);
        when(workspaceSessionRepository.appendMessage(eq(1L), eq(created), eq("assistant"), anyString(), eq("CARD"), anyString(), eq(1)))
                .thenReturn(new AgentMessageEntity());

        OpportunityPrepareResponse response = service.prepare(1L, "doc-83428");

        assertEquals("WS-fesession001", response.workspaceId());
    }

    @Test
    void detailFallsBackToSearchByDocIdWhenDocChunksUnavailable() {
        when(ragForgeClient.searchJdByDocId(83428L, 50)).thenReturn(List.of(
                chunk(1L, 83428L, SAMPLE_JD, 0.9)
        ));

        OpportunityDetailVO detail = service.detail(1L, "doc-83428");

        assertEquals("doc-83428", detail.jdId());
        assertTrue(detail.jdContent() != null && !detail.jdContent().isBlank());
    }

    @Test
    void detailFoundViaDirectFetchWhenSearchMisses() {
        when(ragForgeClient.fetchDocumentChunks(12598L)).thenReturn(List.of(
                chunk(1L, 12598L, SAMPLE_JD, 0.8)
        ));
        when(resumeService.getDefaultActiveResume(1L)).thenReturn(Optional.empty());

        OpportunityDetailVO detail = service.detail(1L, "doc-12598");

        assertEquals("doc-12598", detail.jdId());
        verify(ragForgeClient, times(0)).searchJd(anyString(), eq(50));
    }

    @Test
    void detailFoundReturnsJdContent() {
        when(ragForgeClient.fetchDocumentChunks(55L)).thenReturn(List.of(
                chunk(1L, 55L, SAMPLE_JD, 0.8),
                chunk(2L, 55L, "补充描述", 0.6)
        ));
        when(resumeService.getDefaultActiveResume(1L)).thenReturn(Optional.empty());

        OpportunityDetailVO detail = service.detail(1L, "doc-55");

        assertNotNull(detail.jdContent());
        assertTrue(detail.jdContent().contains("信号处理算法"));
        assertEquals("doc-55", detail.jdId());
    }

    @Test
    void listWithResumeMatchScoreWithinRange() {
        when(ragForgeClient.searchJd("Java", 30)).thenReturn(List.of(chunk(1L, 1L, SAMPLE_JD, 0.87)));
        ResumeEntity resume = new ResumeEntity();
        resume.setContent("Java Redis 算法设计 项目经验");
        when(resumeService.getDefaultActiveResume(1L)).thenReturn(Optional.of(resume));

        PageResult<OpportunityListItemVO> result =
                service.list(1L, new OpportunityListRequest("Java", null, null, null, 1, 10));

        int score = result.items().get(0).matchScore();
        assertTrue(score >= 0 && score <= 100);
    }

    @Test
    void listDoesNotWriteCacheOnMiss() {
        when(ragForgeClient.searchJd("Java", 30)).thenReturn(List.of(chunk(1L, 1L, SAMPLE_JD, 0.6)));
        when(resumeService.getDefaultActiveResume(1L)).thenReturn(Optional.empty());

        service.list(1L, new OpportunityListRequest("Java", null, null, null, 1, 10));

        verify(redisTemplate, times(0)).opsForValue();
    }

    @Test
    void listDemoModeQueryIncludesKeyword() {
        when(ragForgeClient.searchJd("Python 广州 Java 3-5年", 30))
                .thenReturn(List.of(chunk(1L, 1L, SAMPLE_JD, 0.6)));
        when(resumeService.getDefaultActiveResume(1L)).thenReturn(Optional.empty());

        service.list(1L, new OpportunityListRequest("Python", null, null, "demo", 1, 10));

        verify(ragForgeClient).searchJd("Python 广州 Java 3-5年", 30);
    }

    @Test
    void listExpandsSearchTopKForLaterPages() {
        when(ragForgeClient.searchJd("Java", 40)).thenReturn(List.of(chunk(1L, 1L, SAMPLE_JD, 0.6)));
        when(resumeService.getDefaultActiveResume(1L)).thenReturn(Optional.empty());

        service.list(1L, new OpportunityListRequest("Java", null, null, null, 4, 10));

        verify(ragForgeClient).searchJd("Java", 40);
    }

    @Test
    void listSecondPageReturnsNextSlice() {
        List<RagForgeChunk> chunks = new java.util.ArrayList<>();
        for (long i = 1; i <= 12; i++) {
            chunks.add(chunk(i, i, SAMPLE_JD.replace("星天科技", "公司" + i), 1.0 - i / 100.0));
        }
        when(ragForgeClient.searchJd("Java", 30)).thenReturn(chunks);
        when(resumeService.getDefaultActiveResume(1L)).thenReturn(Optional.empty());

        PageResult<OpportunityListItemVO> result =
                service.list(1L, new OpportunityListRequest("Java", null, null, null, 2, 10));

        assertEquals(12, result.total());
        assertEquals(2, result.page());
        assertEquals(2, result.items().size());
        assertEquals("公司11", result.items().get(0).company());
        assertEquals("公司12", result.items().get(1).company());
    }

    private static RagForgeChunk chunk(Long chunkId, Long docId, String content, double score) {
        return new RagForgeChunk(chunkId, docId, "file.md", content, "JD", score);
    }

    private static CareerProfileResponse emptyProfile() {
        return CareerProfileResponse.builder()
                .targetRole(null)
                .targetCity(null)
                .skillKeywords(List.of())
                .build();
    }
}
