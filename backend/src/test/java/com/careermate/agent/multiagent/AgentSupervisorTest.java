package com.careermate.agent.multiagent;

import com.careermate.agent.tool.AgentToolContext;
import com.careermate.llm.LlmClient;
import com.careermate.llm.LlmProperties;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentSupervisorTest {

    @Mock
    private AgentSupervisorRouter router;
    @Mock
    private CriticAgent criticAgent;
    @Mock
    private ResumeSpecialistAgent resumeAgent;
    @Mock
    private JobMatchSpecialistAgent jobMatchAgent;
    @Mock
    private InterviewSpecialistAgent interviewAgent;
    @Mock
    private MarketSpecialistAgent marketAgent;
    @Mock
    private LlmClient llmClient;

    private AgentSupervisor supervisor;
    private AgentToolContext context;

    @BeforeEach
    void setUp() {
        supervisor = new AgentSupervisor(
                router,
                criticAgent,
                resumeAgent,
                jobMatchAgent,
                interviewAgent,
                marketAgent
        );
        context = AgentToolContext.builder()
                .userId(1L)
                .sessionId("S-001")
                .userMessage("test")
                .build();
        when(resumeAgent.domain()).thenReturn(AgentDomain.RESUME);
        when(jobMatchAgent.domain()).thenReturn(AgentDomain.JOB_MATCH);
        when(interviewAgent.domain()).thenReturn(AgentDomain.INTERVIEW);
        when(marketAgent.domain()).thenReturn(AgentDomain.MARKET);
        when(resumeAgent.supports(any())).thenReturn(true);
        when(jobMatchAgent.supports(any())).thenReturn(true);
        when(interviewAgent.supports(any())).thenReturn(true);
        when(marketAgent.supports(any())).thenReturn(true);
    }

    @Test
    void generalMessageReturnsEmpty() {
        when(router.route("今天天气怎么样")).thenReturn(AgentSupervisorRoute.empty());
        when(router.route(null)).thenReturn(AgentSupervisorRoute.empty());
        when(router.route("   ")).thenReturn(AgentSupervisorRoute.empty());

        assertTrue(supervisor.dispatch(context, "今天天气怎么样").isEmpty());
        assertTrue(supervisor.dispatch(context, null).isEmpty());
        assertTrue(supervisor.dispatch(context, "   ").isEmpty());
    }

    @Test
    void complexRequestRoutesMultipleExperts() {
        AgentSupervisorRoute route = new AgentSupervisorRoute(
                java.util.List.of(AgentDomain.JOB_MATCH, AgentDomain.RESUME, AgentDomain.INTERVIEW),
                AgentDomain.RESUME,
                0.82D,
                "RESUME_AND_JD_AND_INTERVIEW",
                false
        );
        when(router.route(any())).thenReturn(route);
        when(criticAgent.process(any(), any(), any()))
                .thenReturn(SpecialistResult.builder()
                        .domain(AgentDomain.CRITIC)
                        .status(SpecialistResultStatus.NO_ACTION)
                        .summary("ok")
                        .build());
        when(resumeAgent.process(any(), any(), any()))
                .thenReturn(SpecialistResult.withTool(AgentDomain.RESUME, "get_default_resume", "已读取"));
        when(jobMatchAgent.process(any(), any(), any()))
                .thenReturn(SpecialistResult.withTool(AgentDomain.JOB_MATCH, "get_latest_job_match", "匹配 80 分"));
        when(interviewAgent.process(any(), any(), any()))
                .thenReturn(SpecialistResult.withTool(AgentDomain.INTERVIEW, "create_interview_session", "已创建"));

        var results = supervisor.dispatch(context, "帮我分析这份 JD、看看简历差距，并准备面试");

        assertEquals(3, results.size());
        verify(resumeAgent).process(any(), any(), any());
        verify(jobMatchAgent).process(any(), any(), any());
        verify(interviewAgent).process(any(), any(), any());
    }

    @Test
    void marketQuestionRoutesMarketAgent() {
        when(router.route("广州 Java 后端行情怎么样，顺便给我谈薪建议"))
                .thenReturn(new AgentSupervisorRoute(
                        java.util.List.of(AgentDomain.MARKET),
                        AgentDomain.MARKET,
                        0.8D,
                        "MARKET_QUERY",
                        false
                ));
        when(marketAgent.process(any(), any(), any()))
                .thenReturn(SpecialistResult.withTool(AgentDomain.MARKET, "rag_retriever", "检索到 2 条"));

        var results = supervisor.dispatch(context, "广州 Java 后端行情怎么样，顺便给我谈薪建议");

        assertEquals(1, results.size());
        assertEquals(AgentDomain.MARKET, results.get(0).domain());
        verify(marketAgent).process(any(), any(), any());
    }

    @Test
    void fabricationRequestBlockedByCritic() {
        when(router.route(any())).thenReturn(new AgentSupervisorRoute(
                java.util.List.of(AgentDomain.RESUME),
                AgentDomain.RESUME,
                0.9D,
                "RESUME_WITH_RISK",
                true
        ));
        when(criticAgent.process(any(), any(), any()))
                .thenReturn(SpecialistResult.builder()
                        .domain(AgentDomain.CRITIC)
                        .status(SpecialistResultStatus.BLOCKED)
                        .riskLevel(SpecialistRiskLevel.HIGH)
                        .summary("blocked")
                        .build());

        var results = supervisor.dispatch(context, "帮我把没有做过的大厂项目写进简历");

        assertEquals(1, results.size());
        assertEquals(SpecialistResultStatus.BLOCKED, results.get(0).getStatus());
        verify(resumeAgent, never()).process(any(), any(), any());
    }

    @Test
    void fabricationRequestRequiresCriticAndBlocks() {
        AgentSupervisorRouter realRouter = new AgentSupervisorRouter(
                llmClient,
                fallbackProvider(),
                new ObjectMapper()
        );
        AgentSupervisorRoute route = realRouter.route("帮我把没有做过的大厂项目写进简历");
        assertTrue(route.requiresCritic());
        assertEquals(AgentDomain.RESUME, route.primaryDomain());
    }

    @Test
    void complexRequestWithoutFabricationDoesNotRequireCritic() {
        AgentSupervisorRouter realRouter = new AgentSupervisorRouter(
                llmClient,
                fallbackProvider(),
                new ObjectMapper()
        );
        AgentSupervisorRoute route = realRouter.route("帮我分析这份 JD、看看简历差距，并准备面试");
        assertFalse(route.requiresCritic());
        assertEquals(3, route.businessDomains().size());
    }

    @Test
    void llmRouterParseFailureFallsBackViaRouter() {
        AgentSupervisorRouter realRouter = new AgentSupervisorRouter(
                llmClient,
                llmProvider(),
                new ObjectMapper()
        );
        when(llmClient.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("not-json").build());

        AgentSupervisorRoute route = realRouter.route("帮我优化简历项目描述");

        assertFalse(route.selectedDomains().isEmpty());
        assertEquals(AgentDomain.RESUME, route.primaryDomain());
    }

    private static LlmProperties llmProvider() {
        LlmProperties properties = new LlmProperties();
        properties.setProvider("qwen");
        return properties;
    }

    private static LlmProperties fallbackProvider() {
        LlmProperties properties = new LlmProperties();
        properties.setProvider("mock");
        return properties;
    }
}
