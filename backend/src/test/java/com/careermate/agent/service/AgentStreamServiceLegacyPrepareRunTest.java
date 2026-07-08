package com.careermate.agent.service;

import com.careermate.agent.config.AgentKernelProperties;
import com.careermate.agent.config.AgentProperties;
import com.careermate.agent.context.AgentConversationContextProvider;
import com.careermate.agent.context.CareerProfileContextProvider;
import com.careermate.agent.context.CareerProfileContextResult;
import com.careermate.agent.context.ConversationContextResult;
import com.careermate.agent.memory.AgentMemoryService;
import com.careermate.agent.multiagent.AgentDomain;
import com.careermate.agent.multiagent.AgentSupervisor;
import com.careermate.agent.multiagent.SpecialistResult;
import com.careermate.agent.multiagent.SpecialistResultStatus;
import com.careermate.agent.multiagent.SpecialistRiskLevel;
import com.careermate.agent.react.ReActEngine;
import com.careermate.agent.react.ReActTrace;
import com.careermate.agent.runtime.AgentKernelService;
import com.careermate.agent.session.AgentSessionService;
import com.careermate.agent.sse.AgentTaskRegistry;
import com.careermate.agent.sse.SseEmitterService;
import com.careermate.agent.tool.AgentLlmIntentRecognizer;
import com.careermate.agent.tool.AgentToolExecutionService;
import com.careermate.agent.tool.AgentToolResult;
import com.careermate.agent.tool.AgentToolRouter;
import com.careermate.jobmatch.JobMatchContext;
import com.careermate.jobmatch.JobMatchContextProvider;
import com.careermate.llm.LlmClient;
import com.careermate.llm.LlmProperties;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.observability.AgentTracing;
import com.careermate.profile.CareerProfileUpdateResult;
import com.careermate.profile.service.CareerProfileAutoUpdateService;
import com.careermate.prompt.PromptProperties;
import com.careermate.prompt.PromptTemplateRegistry;
import com.careermate.prompt.PromptTemplateService;
import com.careermate.resume.ResumeContext;
import com.careermate.resume.ResumeContextProvider;
import com.careermate.workspace.support.WorkspaceSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.task.SyncTaskExecutor;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentStreamServiceLegacyPrepareRunTest {

    @Mock
    private LlmClient llmClient;
    @Mock
    private SseEmitterService sseEmitterService;
    @Mock
    private AgentTaskRegistry taskRegistry;
    @Mock
    private AgentSessionService agentSessionService;
    @Mock
    private ResumeContextProvider resumeContextProvider;
    @Mock
    private JobMatchContextProvider jobMatchContextProvider;
    @Mock
    private AgentProperties agentProperties;
    @Mock
    private AgentLlmIntentRecognizer intentRecognizer;
    @Mock
    private AgentToolExecutionService agentToolExecutionService;
    @Mock
    private AgentConversationContextProvider conversationContextProvider;
    @Mock
    private CareerProfileContextProvider careerProfileContextProvider;
    @Mock
    private CareerProfileAutoUpdateService careerProfileAutoUpdateService;
    @Mock
    private AgentMemoryService agentMemoryService;
    @Mock
    private AgentTracing agentTracing;
    @Mock
    private LlmProperties llmProperties;
    @Mock
    private AgentSupervisor agentSupervisor;
    @Mock
    private ReActEngine reactEngine;
    @Mock
    private WorkspaceSessionRepository workspaceSessionRepository;
    @Mock
    private AgentKernelService agentKernelService;
    @Mock
    private AgentKernelProperties agentKernelProperties;

    private AgentStreamService agentStreamService;

    @BeforeEach
    void setUp() {
        agentStreamService = new AgentStreamService(
                llmClient,
                new SyncTaskExecutor(),
                sseEmitterService,
                taskRegistry,
                agentSessionService,
                resumeContextProvider,
                jobMatchContextProvider,
                new ObjectMapper(),
                agentProperties,
                intentRecognizer,
                agentToolExecutionService,
                conversationContextProvider,
                careerProfileContextProvider,
                careerProfileAutoUpdateService,
                agentMemoryService,
                agentTracing,
                llmProperties,
                agentSupervisor,
                reactEngine,
                workspaceSessionRepository,
                agentKernelService,
                agentKernelProperties,
                new PromptTemplateService(new PromptTemplateRegistry(), new PromptProperties()),
                org.mockito.Mockito.mock(ChatClientStreamAdapter.class),
                new com.careermate.agent.path.AgentPathRouter(),
                org.mockito.Mockito.mock(com.careermate.agent.rag.DeepPathKnowledgeAugmentor.class),
                org.mockito.Mockito.mock(com.careermate.agent.reflect.DeepPathReflectionRunner.class),
                new com.careermate.agent.cost.TokenCostRecorder(
                        new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
                        new com.careermate.agent.cost.LlmPricingProperties()),
                org.mockito.Mockito.mock(com.careermate.agent.memory.ltm.LongTermMemoryAdvisor.class)
        );
        stubContextLoaders();
    }

    @Test
    void legacyPrepareRunRecordsPromptTemplateTrace() throws Exception {
        when(agentSupervisor.dispatch(any(), anyString()))
                .thenReturn(List.of());

        invokePrepareLegacyRun(1L, "legacy-prompt-trace", "请帮我分析当前求职进展");

        ArgumentCaptor<String> requestSummaryCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> responseSummaryCaptor = ArgumentCaptor.forClass(String.class);
        verify(agentSessionService).recordTrace(
                eq(1L),
                eq("legacy-prompt-trace"),
                eq("prompt_template"),
                requestSummaryCaptor.capture(),
                responseSummaryCaptor.capture(),
                eq("SUCCESS"),
                isNull(),
                isNull()
        );

        String requestSummary = requestSummaryCaptor.getValue();
        String responseSummary = responseSummaryCaptor.getValue();
        assertTrue(requestSummary.contains("\"promptId\":\"agent-base\""));
        assertTrue(responseSummary.contains("\"promptId\":\"agent-base\""));
        assertTrue(responseSummary.contains("\"version\":\"v1\""));
        assertFalse(requestSummary.contains("CareerMate"));
        assertFalse(requestSummary.contains("小职"));
        assertFalse(responseSummary.contains("CareerMate"));
        assertFalse(responseSummary.contains("小职"));
        assertFalse(requestSummary.contains("【上下文规则】"));
        assertFalse(responseSummary.contains("【上下文规则】"));
    }

    @Test
    void legacyBlockedSpecialistSkipsReActAndResumeWriteTools() throws Exception {
        String userMessage = "帮我把没有做过的大厂项目写进简历并生成 PDF 简历";
        when(agentSupervisor.dispatch(any(), eq(userMessage)))
                .thenReturn(List.of(SpecialistResult.builder()
                        .domain(AgentDomain.CRITIC)
                        .agentName("CriticAgent")
                        .status(SpecialistResultStatus.BLOCKED)
                        .riskLevel(SpecialistRiskLevel.HIGH)
                        .summary("不能编造经历")
                        .build()));

        ChatRequest chatRequest = invokePrepareLegacyRun(1L, "legacy-blocked", userMessage);
        String prompt = chatRequest.getMessages().get(0).getContent();

        verify(reactEngine, never()).run(any(), anyString(), anyString());
        verify(agentToolExecutionService, never()).execute(any(), eq("generate_resume_from_jd"));
        verify(intentRecognizer, never()).route(anyString());
        assertTrue(prompt.contains("BLOCKED"));
        assertTrue(prompt.contains("不能编造经历"));
    }

    @Test
    void legacyNoActionSpecialistStillRunsLegacyToolFallback() throws Exception {
        String userMessage = "请帮我查看当前求职进展和看板统计数据详情";
        when(agentSupervisor.dispatch(any(), eq(userMessage)))
                .thenReturn(List.of(SpecialistResult.builder()
                        .domain(AgentDomain.RESUME)
                        .status(SpecialistResultStatus.NO_ACTION)
                        .summary("no tool executed")
                        .build()));
        when(intentRecognizer.route(userMessage))
                .thenReturn(Optional.of(new AgentToolRouter.RoutedTool(
                        "get_dashboard_overview",
                        Map.of()
                )));
        when(agentToolExecutionService.execute(any(), eq("get_dashboard_overview")))
                .thenReturn(AgentToolResult.builder()
                        .toolName("get_dashboard_overview")
                        .success(true)
                        .summary("看板概览")
                        .build());
        when(agentToolExecutionService.startSummary("get_dashboard_overview"))
                .thenReturn("读取看板");

        ChatRequest chatRequest = invokePrepareLegacyRun(1L, "legacy-no-action", userMessage);
        String prompt = chatRequest.getMessages().get(0).getContent();

        verify(agentToolExecutionService).execute(any(), eq("get_dashboard_overview"));
        assertTrue(prompt.contains("工具调用结果"));
        assertTrue(prompt.contains("看板概览"));
    }

    private ChatRequest invokePrepareLegacyRun(Long userId, String sessionId, String userMessage) throws Exception {
        Method method = AgentStreamService.class.getDeclaredMethod(
                "prepareLegacyRun",
                Long.class,
                String.class,
                String.class,
                com.careermate.agent.path.AgentPathMode.class
        );
        method.setAccessible(true);
        return (ChatRequest) method.invoke(
                agentStreamService, userId, sessionId, userMessage,
                com.careermate.agent.path.AgentPathMode.FAST
        );
    }

    private void stubContextLoaders() {
        when(careerProfileAutoUpdateService.tryAutoUpdateTargetRole(any(), anyString()))
                .thenReturn(CareerProfileUpdateResult.notUpdated());
        when(careerProfileContextProvider.load(any(), anyString()))
                .thenReturn(CareerProfileContextResult.empty());
        when(resumeContextProvider.getResumeContext(any()))
                .thenReturn(ResumeContext.builder().available(false).build());
        when(jobMatchContextProvider.getLatestJobMatchContext(any()))
                .thenReturn(JobMatchContext.builder().available(false).build());
        when(conversationContextProvider.load(any(), anyString(), anyString()))
                .thenReturn(ConversationContextResult.empty());
        when(workspaceSessionRepository.getSessionIfExists(any(), anyString()))
                .thenReturn(null);
        when(reactEngine.run(any(), anyString(), anyString()))
                .thenReturn(new ReActTrace(List.of(), false, 0));
        when(agentTracing.call(any(), any(), any(), any(), any(), any(), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(6);
                    return supplier.get();
                });
    }
}
