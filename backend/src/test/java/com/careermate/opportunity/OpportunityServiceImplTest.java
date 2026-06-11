package com.careermate.opportunity;

import com.careermate.common.api.PageResult;
import com.careermate.common.exception.BizException;
import com.careermate.model.entity.ResumeEntity;
import com.careermate.opportunity.dto.OpportunityDetailVO;
import com.careermate.opportunity.dto.OpportunityListItemVO;
import com.careermate.opportunity.dto.OpportunityListRequest;
import com.careermate.opportunity.dto.OpportunityPrepareResponse;
import com.careermate.model.entity.AgentMessageEntity;
import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.opportunity.service.impl.OpportunityServiceImpl;
import com.careermate.profile.CareerProfileService;
import com.careermate.workspace.support.WorkspaceSessionRepository;
import com.careermate.profile.dto.CareerProfileResponse;
import com.careermate.ragforge.RagForgeChunk;
import com.careermate.ragforge.RagForgeClient;
import com.careermate.resume.ResumeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
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

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new OpportunityServiceImpl(
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

        service.list(1L, new OpportunityListRequest(null, null, null, 1, 10));

        verify(ragForgeClient).searchJd("Java 后端", 30);
    }

    @Test
    void listKeywordEmptyWithProfileUsesRoleAndCity() {
        when(careerProfileService.getProfile(2L)).thenReturn(CareerProfileResponse.builder()
                .targetRole("Java 后端")
                .targetCity("北京")
                .build());
        when(ragForgeClient.searchJd("Java 后端 北京", 30)).thenReturn(List.of());

        service.list(2L, new OpportunityListRequest(null, null, null, 1, 10));

        verify(ragForgeClient).searchJd("Java 后端 北京", 30);
    }

    @Test
    void listKeywordPassedThrough() {
        when(ragForgeClient.searchJd("Redis", 30)).thenReturn(List.of());

        service.list(1L, new OpportunityListRequest("Redis", null, null, 1, 10));

        verify(ragForgeClient).searchJd("Redis", 30);
        verify(careerProfileService, times(0)).getProfile(any());
    }

    @Test
    void listRagForgeEmptyReturnsZeroTotal() {
        when(ragForgeClient.searchJd(anyString(), eq(30))).thenReturn(List.of());

        PageResult<OpportunityListItemVO> result =
                service.list(1L, new OpportunityListRequest("test", null, null, 1, 10));

        assertEquals(0, result.total());
        assertTrue(result.items().isEmpty());
    }

    @Test
    void listMultiChunkAggregation() {
        when(ragForgeClient.searchJd("Java", 30)).thenReturn(List.of(
                new RagForgeChunk(2L, 100L, "f.md", "尾部", "JD", 0.5),
                new RagForgeChunk(1L, 100L, "f.md", SAMPLE_JD, "JD", 0.8)
        ));
        when(resumeService.getDefaultActiveResume(1L)).thenReturn(Optional.empty());

        PageResult<OpportunityListItemVO> result =
                service.list(1L, new OpportunityListRequest("Java", null, null, 1, 10));

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
                service.list(1L, new OpportunityListRequest("Java", null, null, 1, 10));

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
                service.list(1L, new OpportunityListRequest("Java", null, null, 1, 10));

        assertFalse(result.hasResume());
        assertEquals("LATEST", result.sortStrategy());
        assertNull(result.items().get(0).matchScore());
        assertEquals("UNKNOWN", result.items().get(0).matchTier());
        assertEquals(0.9, result.items().get(0).ragScore());
    }

    @Test
    void listCacheHitSkipsSecondRagForgeCall() throws Exception {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        PageResult<OpportunityListItemVO> cached = new PageResult<>(
                1, 1, 10, false, "LATEST",
                List.of(new OpportunityListItemVO(
                        "doc-1", 1L, "星天科技", "算法工程师", null, "北京",
                        "1-3年", 1, 3, "硕士", "100-499人", "2026-06-09",
                        null, "UNKNOWN", List.of(), List.of("Java"), 0.8, null
                ))
        );
        String cachedJson = objectMapper.writeValueAsString(cached);
        when(valueOps.get(anyString())).thenReturn(cachedJson);

        service.list(1L, new OpportunityListRequest("cached-query", null, null, 1, 10));
        service.list(1L, new OpportunityListRequest("cached-query", null, null, 1, 10));

        verify(ragForgeClient, times(0)).searchJd(anyString(), any(Integer.class));
    }

    @Test
    void listRedisUnavailableStillReturnsResult() {
        OpportunityServiceImpl degraded = new OpportunityServiceImpl(
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
                degraded.list(1L, new OpportunityListRequest("Java", null, null, 1, 10));

        assertEquals(1, result.total());
    }

    @Test
    void detailInvalidJdIdThrowsBizException() {
        assertThrows(BizException.class, () -> service.detail(1L, "bad-id"));
    }

    @Test
    void prepareCreatesJdPrepSessionAndWelcomeCard() {
        when(workspaceSessionRepository.findActiveJdPrepSession(1L, "doc-55")).thenReturn(null);
        when(ragForgeClient.searchJd(anyString(), eq(50))).thenReturn(List.of(
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
        when(ragForgeClient.searchJd(anyString(), eq(50))).thenReturn(List.of());

        assertThrows(BizException.class, () -> service.prepare(1L, "doc-999"));
    }

    @Test
    void detailFoundReturnsJdContent() {
        when(ragForgeClient.searchJd(anyString(), eq(50))).thenReturn(List.of(
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
                service.list(1L, new OpportunityListRequest("Java", null, null, 1, 10));

        int score = result.items().get(0).matchScore();
        assertTrue(score >= 0 && score <= 100);
    }

    @Test
    void listWritesCacheOnMiss() throws Exception {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(ragForgeClient.searchJd("Java", 30)).thenReturn(List.of(chunk(1L, 1L, SAMPLE_JD, 0.6)));
        when(resumeService.getDefaultActiveResume(1L)).thenReturn(Optional.empty());

        service.list(1L, new OpportunityListRequest("Java", null, null, 1, 10));

        verify(valueOps, atLeastOnce()).set(anyString(), anyString(), any(Duration.class));
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
