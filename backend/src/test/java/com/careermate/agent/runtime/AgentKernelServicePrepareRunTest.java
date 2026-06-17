package com.careermate.agent.runtime;

import com.careermate.agent.context.AgentConversationContextProvider;
import com.careermate.agent.context.CareerProfileContextProvider;
import com.careermate.agent.context.CareerProfileContextResult;
import com.careermate.agent.context.ConversationContextResult;
import com.careermate.agent.multiagent.AgentDomain;
import com.careermate.agent.multiagent.AgentSupervisor;
import com.careermate.agent.multiagent.SpecialistResult;
import com.careermate.agent.multiagent.SpecialistResultStatus;
import com.careermate.agent.multiagent.SpecialistRiskLevel;
import com.careermate.agent.react.ReActEngine;
import com.careermate.agent.react.ReActTrace;
import com.careermate.agent.tool.AgentLlmIntentRecognizer;
import com.careermate.agent.tool.AgentToolExecutionService;
import com.careermate.agent.tool.AgentToolResult;
import com.careermate.agent.tool.AgentToolRouter;
import com.careermate.jobmatch.JobMatchContext;
import com.careermate.jobmatch.JobMatchContextProvider;
import com.careermate.observability.AgentTracing;
import com.careermate.profile.CareerProfileUpdateResult;
import com.careermate.profile.service.CareerProfileAutoUpdateService;
import com.careermate.resume.ResumeContext;
import com.careermate.resume.ResumeContextProvider;
import com.careermate.workspace.support.WorkspaceSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentKernelServicePrepareRunTest {

    @Mock
    private ResumeContextProvider resumeContextProvider;
    @Mock
    private JobMatchContextProvider jobMatchContextProvider;
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
    private AgentTracing agentTracing;
    @Mock
    private AgentSupervisor agentSupervisor;
    @Mock
    private ReActEngine reactEngine;
    @Mock
    private WorkspaceSessionRepository workspaceSessionRepository;

    private AgentKernelService agentKernelService;

    @BeforeEach
    void setUp() {
        agentKernelService = new AgentKernelService(
                resumeContextProvider,
                jobMatchContextProvider,
                new ObjectMapper(),
                intentRecognizer,
                agentToolExecutionService,
                conversationContextProvider,
                careerProfileContextProvider,
                careerProfileAutoUpdateService,
                agentTracing,
                agentSupervisor,
                reactEngine,
                workspaceSessionRepository
        );
        stubContextLoaders();
    }

    @Test
    void blockedSpecialistSkipsReActAndResumeWriteTools() {
        String userMessage = "帮我把没有做过的大厂项目写进简历并生成 PDF 简历";
        when(agentSupervisor.dispatch(any(), eq(userMessage)))
                .thenReturn(List.of(SpecialistResult.builder()
                        .domain(AgentDomain.CRITIC)
                        .agentName("CriticAgent")
                        .status(SpecialistResultStatus.BLOCKED)
                        .riskLevel(SpecialistRiskLevel.HIGH)
                        .summary("不能编造经历")
                        .build()));

        AgentRunResult result = agentKernelService.prepareRun(AgentRunRequest.builder()
                .userId(1L)
                .sessionId("blocked-session")
                .userMessage(userMessage)
                .build());

        verify(reactEngine, never()).run(any(), anyString(), anyString());
        verify(agentToolExecutionService, never()).execute(any(), eq("generate_resume_from_jd"));
        verify(intentRecognizer, never()).route(anyString());
        assertTrue(result.getSystemPrompt().contains("BLOCKED"));
        assertTrue(result.getSystemPrompt().contains("不能编造经历"));
        assertTrue(result.getToolResults().isEmpty());
        assertFalse(result.getReactTrace().hasSteps());
    }

    @Test
    void noActionSpecialistStillRunsLegacyToolFallback() {
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

        AgentRunResult result = agentKernelService.prepareRun(AgentRunRequest.builder()
                .userId(1L)
                .sessionId("no-action-session")
                .userMessage(userMessage)
                .build());

        verify(agentToolExecutionService).execute(any(), eq("get_dashboard_overview"));
        assertTrue(result.getSystemPrompt().contains("工具调用结果"));
        assertTrue(result.getSystemPrompt().contains("看板概览"));
        assertFalse(result.getToolResults().isEmpty());
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
