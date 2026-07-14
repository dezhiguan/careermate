package com.careermate.resume.version.workflow;

import com.careermate.agent.session.AgentSessionService;
import com.careermate.llm.LlmClient;
import com.careermate.llm.StreamCallback;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.prompt.PromptProperties;
import com.careermate.prompt.PromptTemplateRegistry;
import com.careermate.prompt.PromptTemplateService;
import com.careermate.ragforge.RagForgeChunk;
import com.careermate.ragforge.RagForgeClient;
import com.careermate.resume.ResumeContext;
import com.careermate.resume.ResumeContextProvider;
import com.careermate.resume.version.dto.ResumeVersionVO;
import com.careermate.resume.version.service.ResumeVersionService;
import com.careermate.workspace.support.WorkspaceSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateResumeWorkflowRunnerTest {

    @Mock
    private WorkspaceSessionRepository workspaceSessionRepository;
    @Mock
    private ResumeContextProvider resumeContextProvider;
    @Mock
    private RagForgeClient ragForgeClient;
    @Mock
    private LlmClient llmClient;
    @Mock
    private ResumeVersionService resumeVersionService;
    @Mock
    private AgentSessionService agentSessionService;
    @Mock
    private com.careermate.jobmatch.JobMatchAnalyzer jobMatchAnalyzer;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PromptTemplateService promptTemplateService;
    private GenerateResumeWorkflowRunner runner;

    @BeforeEach
    void setUp() {
        promptTemplateService = new PromptTemplateService(new PromptTemplateRegistry(), new PromptProperties());
        runner = new GenerateResumeWorkflowRunner(
                workspaceSessionRepository,
                resumeContextProvider,
                ragForgeClient,
                llmClient,
                resumeVersionService,
                objectMapper,
                promptTemplateService,
                jobMatchAnalyzer
        );
    }

    @Test
    void generateResumeUsesResumeGenerateFromJdPromptId() {
        stubHappyPath();
        mockLlm("# 简历\n\n## 专业技能\nJava");

        GenerateResumeWorkflowEventSink sink = new GenerateResumeWorkflowEventSink(agentSessionService, 1L, "WS-abc");
        GenerateResumeWorkflowRun run = new GenerateResumeWorkflowRun(1L, "WS-abc", "doc-1", null);
        runner.execute(run, sink);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmClient).streamChat(requestCaptor.capture(), any(StreamCallback.class));
        String systemContent = requestCaptor.getValue().getMessages().get(0).getContent();
        assertTrue(systemContent.contains("简历操盘手小职"));
        assertFalse(systemContent.contains("模板 v2"));

        verify(agentSessionService).recordTrace(
                eq(1L), eq("WS-abc"),
                eq(GenerateResumeWorkflowStep.GENERATE_RESUME.traceName()),
                anyString(),
                argThat(summary -> summary.contains("\"promptId\":\"resume-generate-from-jd\"")
                        && summary.contains("\"promptVersion\":\"v1\"")),
                eq("SUCCESS"), anyLong(), isNull()
        );
    }

    @Test
    void activeVersionOverrideRollsBackResumePrompt() {
        PromptProperties properties = new PromptProperties();
        properties.getActiveVersions().put("resume-generate-from-jd", "v2");
        promptTemplateService = new PromptTemplateService(new PromptTemplateRegistry(), properties);
        runner = new GenerateResumeWorkflowRunner(
                workspaceSessionRepository,
                resumeContextProvider,
                ragForgeClient,
                llmClient,
                resumeVersionService,
                objectMapper,
                promptTemplateService,
                jobMatchAnalyzer
        );

        stubHappyPath();
        mockLlm("# 简历\n\n## 专业技能\nJava");

        GenerateResumeWorkflowEventSink sink = new GenerateResumeWorkflowEventSink(agentSessionService, 1L, "WS-abc");
        GenerateResumeWorkflowRun run = new GenerateResumeWorkflowRun(1L, "WS-abc", "doc-1", null);
        runner.execute(run, sink);

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmClient).streamChat(requestCaptor.capture(), any(StreamCallback.class));
        assertTrue(requestCaptor.getValue().getMessages().get(0).getContent().contains("模板 v2"));

        verify(agentSessionService).recordTrace(
                eq(1L), eq("WS-abc"),
                eq(GenerateResumeWorkflowStep.GENERATE_RESUME.traceName()),
                anyString(),
                argThat(summary -> summary.contains("\"promptId\":\"resume-generate-from-jd\"")
                        && summary.contains("\"promptVersion\":\"v2\"")),
                eq("SUCCESS"), anyLong(), isNull()
        );
    }

    private void stubHappyPath() {
        AgentSessionEntity session = new AgentSessionEntity();
        session.setId(5L);
        session.setUserId(1L);
        session.setSessionId("WS-abc");
        session.setJdId("doc-1");
        session.setJdSnapshot("{\"company\":\"腾讯\",\"title\":\"算法工程师\"}");
        when(workspaceSessionRepository.requireSession(1L, "WS-abc")).thenReturn(session);
        when(resumeContextProvider.getResumeContext(1L)).thenReturn(
                ResumeContext.builder().available(true).resumeId(10L).content("原始简历").build()
        );
        when(ragForgeClient.fetchDocumentChunks(1L)).thenReturn(List.of());
        // 修复后：按 docId 精确直取（不再用 searchJd 泛词搜 + 过滤）
        when(ragForgeClient.searchJdByDocId(eq(1L), eq(50))).thenReturn(List.of(
                new RagForgeChunk(1L, 1L, "jd.md", "# JD 内容", "JD", 0.9)
        ));
        when(resumeVersionService.createVersion(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ResumeVersionVO(
                        "ver-1", "针对【腾讯】算法 · v1", "WS-abc", 1L, "腾讯 算法", "腾讯", "算法", 1,
                        "",
                        "# 简历", List.of(), null, OffsetDateTime.now(), null, null
                ));
        when(workspaceSessionRepository.appendMessage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new com.careermate.model.entity.AgentMessageEntity());
    }

    private void mockLlm(String output) {
        doAnswer(invocation -> {
            StreamCallback cb = invocation.getArgument(1);
            cb.onToken(output);
            cb.onComplete(ChatResponse.builder().build());
            return null;
        }).when(llmClient).streamChat(any(ChatRequest.class), any(StreamCallback.class));
    }
}
