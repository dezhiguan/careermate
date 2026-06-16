package com.careermate.resume.version.workflow;

import com.careermate.agent.session.AgentSessionService;
import com.careermate.agent.sse.SseEmitterService;
import com.careermate.agent.sse.SseEventType;
import com.careermate.common.exception.BizException;
import com.careermate.llm.LlmClient;
import com.careermate.llm.StreamCallback;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.model.entity.AgentMessageEntity;
import com.careermate.model.entity.AgentSessionEntity;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateResumeFromJdWorkflowTest {

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
    private SseEmitterService sseEmitterService;

    private GenerateResumeFromJdWorkflow workflow;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        workflow = new GenerateResumeFromJdWorkflow(
                workspaceSessionRepository,
                resumeContextProvider,
                ragForgeClient,
                llmClient,
                resumeVersionService,
                agentSessionService,
                objectMapper
        );
    }

    @Test
    void noResumeThrowsBusinessException() {
        AgentSessionEntity session = jdSession();
        when(workspaceSessionRepository.requireSession(1L, "WS-abc")).thenReturn(session);
        when(resumeContextProvider.getResumeContext(1L)).thenReturn(
                ResumeContext.builder().available(false).build()
        );

        assertThrows(BizException.class, () ->
                workflow.doGenerate(1L, "WS-abc", "doc-1", null)
        );

        verify(agentSessionService).recordTrace(
                eq(1L), eq("WS-abc"),
                eq(GenerateResumeWorkflowStep.LOAD_WORKSPACE.traceName()),
                anyString(), anyString(), eq("SUCCESS"), anyLong(), isNull()
        );
        verify(agentSessionService).recordTrace(
                eq(1L), eq("WS-abc"),
                eq(GenerateResumeWorkflowStep.LOAD_RESUME.traceName()),
                anyString(), eq("{}"), eq("FAILED"), anyLong(),
                eq("WORKFLOW_LOAD_RESUME_FAILED")
        );
        verify(resumeVersionService, never()).createVersion(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void jdNotFoundFailsWithLoadJdTrace() {
        AgentSessionEntity session = jdSession();
        when(workspaceSessionRepository.requireSession(1L, "WS-abc")).thenReturn(session);
        when(resumeContextProvider.getResumeContext(1L)).thenReturn(
                ResumeContext.builder().available(true).resumeId(10L).content("原始简历").build()
        );
        when(ragForgeClient.fetchDocumentChunks(1L)).thenReturn(List.of());
        when(ragForgeClient.searchJd(any(), eq(50))).thenReturn(List.of());

        assertThrows(BizException.class, () ->
                workflow.doGenerate(1L, "WS-abc", "doc-1", null)
        );

        verify(agentSessionService).recordTrace(
                eq(1L), eq("WS-abc"),
                eq(GenerateResumeWorkflowStep.LOAD_JD.traceName()),
                anyString(), eq("{}"), eq("FAILED"), anyLong(),
                eq("WORKFLOW_LOAD_JD_FAILED")
        );
        verify(resumeVersionService, never()).createVersion(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void llmOnErrorFailsWithGenerateResumeTrace() {
        stubHappyPath();
        doAnswer(invocation -> {
            StreamCallback cb = invocation.getArgument(1);
            cb.onError(new RuntimeException("stream down"));
            return null;
        }).when(llmClient).streamChat(any(ChatRequest.class), any(StreamCallback.class));

        assertThrows(BizException.class, () ->
                workflow.doGenerate(1L, "WS-abc", "doc-1", null)
        );

        verify(agentSessionService).recordTrace(
                eq(1L), eq("WS-abc"),
                eq(GenerateResumeWorkflowStep.GENERATE_RESUME.traceName()),
                anyString(), eq("{}"), eq("FAILED"), anyLong(),
                eq("WORKFLOW_GENERATE_RESUME_FAILED")
        );
        verify(resumeVersionService, never()).createVersion(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void emptyMarkdownFailsQualityCheck() {
        stubHappyPath();
        mockLlmStreamOnly("```meta\n{\"changes\":[]}\n```");

        assertThrows(BizException.class, () ->
                workflow.doGenerate(1L, "WS-abc", "doc-1", null)
        );

        verify(agentSessionService).recordTrace(
                eq(1L), eq("WS-abc"),
                eq(GenerateResumeWorkflowStep.QUALITY_CHECK.traceName()),
                anyString(), eq("{}"), eq("FAILED"), anyLong(),
                eq("WORKFLOW_QUALITY_CHECK_FAILED")
        );
        verify(resumeVersionService, never()).createVersion(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void placeholderContentFailsQualityCheck() {
        stubHappyPath();
        mockLlmStreamOnly("# 姓名\n## 工作经历\n公司A 待补充");

        assertThrows(BizException.class, () ->
                workflow.doGenerate(1L, "WS-abc", "doc-1", null)
        );

        verify(agentSessionService).recordTrace(
                eq(1L), eq("WS-abc"),
                eq(GenerateResumeWorkflowStep.QUALITY_CHECK.traceName()),
                anyString(), eq("{}"), eq("FAILED"), anyLong(),
                eq("WORKFLOW_QUALITY_CHECK_FAILED")
        );
        verify(resumeVersionService, never()).createVersion(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void successPathRecordsAllWorkflowStepTraces() {
        stubHappyPath();
        mockLlmOutput("# 简历");

        workflow.doGenerate(1L, "WS-abc", "doc-1", null);

        for (GenerateResumeWorkflowStep step : GenerateResumeWorkflowStep.values()) {
            verify(agentSessionService).recordTrace(
                    eq(1L), eq("WS-abc"),
                    eq(step.traceName()),
                    anyString(), anyString(), eq("SUCCESS"), anyLong(), isNull()
            );
        }
    }

    @Test
    void streamAccumulatesOutput() {
        stubHappyPath();
        doAnswer(invocation -> {
            StreamCallback cb = invocation.getArgument(1);
            cb.onToken("# ");
            cb.onToken("简历");
            cb.onComplete(ChatResponse.builder().build());
            return null;
        }).when(llmClient).streamChat(any(ChatRequest.class), any(StreamCallback.class));
        when(resumeVersionService.createVersion(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ResumeVersionVO(
                        "ver-1", "腾讯 - 算法", "WS-abc", "doc-1", "腾讯 算法",
                        "# 简历", List.of(), null, OffsetDateTime.now()
                ));
        when(workspaceSessionRepository.appendMessage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new AgentMessageEntity());

        workflow.doGenerate(1L, "WS-abc", "doc-1", sseEmitterService);

        verify(sseEmitterService, atLeastOnce())
                .send(eq("WS-abc"), eq(SseEventType.TOKEN), any());
        verify(sseEmitterService).send(eq("WS-abc"), eq(SseEventType.UI_ACTION), any());
        verify(sseEmitterService).send(eq("WS-abc"), eq(SseEventType.DONE), any());
        verify(resumeVersionService).createVersion(
                eq(1L), eq("WS-abc"), eq(10L), eq("doc-1"), any(), any(), any(), any()
        );
    }

    @Test
    void failureSseSendsErrorAndFailedCardWithPayload() {
        AgentSessionEntity session = jdSession();
        when(workspaceSessionRepository.requireSession(1L, "WS-abc")).thenReturn(session);
        when(resumeContextProvider.getResumeContext(1L)).thenReturn(
                ResumeContext.builder().available(false).build()
        );
        when(workspaceSessionRepository.appendMessage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new AgentMessageEntity());

        workflow.generate(1L, "WS-abc", "doc-1", sseEmitterService);

        verify(sseEmitterService).send(eq("WS-abc"), eq(SseEventType.ERROR), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> cardCaptor = ArgumentCaptor.forClass(Map.class);
        verify(sseEmitterService).send(eq("WS-abc"), eq(SseEventType.UI_ACTION), cardCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = cardCaptor.getValue();
        @SuppressWarnings("unchecked")
        Map<String, Object> card = (Map<String, Object>) envelope.get("card");
        assertEquals("GENERATE_FAILED", card.get("type"));
        assertEquals("LOAD_RESUME", card.get("failedStep"));
        assertEquals("doc-1", card.get("jdId"));
        assertNotNull(card.get("payload"));
        verify(sseEmitterService).complete("WS-abc");
    }

    @Test
    void metaBlockParsedIntoOptimizationNotes() {
        stubHappyPath();
        String output = "# 简历正文\n\n```meta\n{\"changes\":[{\"field\":\"技能\"}]}\n```";
        mockLlmOutput(output, "# 简历正文");

        workflow.doGenerate(1L, "WS-abc", "doc-1", null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> notesCaptor = ArgumentCaptor.forClass(List.class);
        verify(resumeVersionService).createVersion(
                eq(1L), eq("WS-abc"), eq(10L), eq("doc-1"), any(), any(), eq("# 简历正文"), notesCaptor.capture()
        );
        assertFalse(notesCaptor.getValue().isEmpty());
    }

    @Test
    void missingMetaBlockUsesEmptyNotes() {
        stubHappyPath();
        mockLlmOutput("# 只有正文", "# 只有正文");

        workflow.doGenerate(1L, "WS-abc", "doc-1", null);

        verify(resumeVersionService).createVersion(
                eq(1L), eq("WS-abc"), eq(10L), eq("doc-1"), any(), any(), eq("# 只有正文"), eq(List.of())
        );
    }

    @Test
    void persistsCardMessage() {
        stubHappyPath();
        mockLlmOutput("# 简历");

        workflow.doGenerate(1L, "WS-abc", "doc-1", null);

        ArgumentCaptor<String> metadataCaptor = ArgumentCaptor.forClass(String.class);
        verify(workspaceSessionRepository).appendMessage(
                eq(1L), any(), eq("assistant"), any(), eq("CARD"), metadataCaptor.capture(), eq(null)
        );
        assertTrue(metadataCaptor.getValue().contains("RESUME_GENERATED"));
    }

    @Test
    void neverCallsSyncText() {
        stubHappyPath();
        mockLlmOutput("# 简历");

        workflow.doGenerate(1L, "WS-abc", "doc-1", null);

        verify(ragForgeClient, never()).syncText(any(), any(), any(), any());
    }

    @Test
    void parseMetaBlockStaticHelper() {
        GenerateResumeFromJdWorkflow.MetaParseResult result = GenerateResumeFromJdWorkflow.parseMetaBlock(
                "正文\n```meta\n{\"changes\":[{\"x\":1}]}\n```"
        );
        assertEquals("正文", result.markdown());
        assertEquals(1, result.changes().size());
    }

    @Test
    void parseMetaBlockStripsWrappingMarkdownFence() {
        GenerateResumeFromJdWorkflow.MetaParseResult result = GenerateResumeFromJdWorkflow.parseMetaBlock(
                "```markdown\n# 简历正文\n## 工作经历\n```\n\n```meta\n{\"changes\":[{\"x\":1}]}\n```"
        );
        assertEquals("# 简历正文\n## 工作经历", result.markdown());
        assertEquals(1, result.changes().size());
    }

    @Test
    void analyzeGapIsRuleBased() {
        String summary = GenerateResumeWorkflowRunner.analyzeGap("Java 后端工程师", "原始简历无章节");
        assertTrue(summary.contains("jdChars="));
        assertTrue(summary.contains("missingSections="));
    }

    private void stubHappyPath() {
        AgentSessionEntity session = jdSession();
        when(workspaceSessionRepository.requireSession(1L, "WS-abc")).thenReturn(session);
        when(resumeContextProvider.getResumeContext(1L)).thenReturn(
                ResumeContext.builder().available(true).resumeId(10L).content("原始简历").build()
        );
        when(ragForgeClient.fetchDocumentChunks(1L)).thenReturn(List.of());
        when(ragForgeClient.searchJd(any(), eq(50))).thenReturn(List.of(
                new RagForgeChunk(1L, 1L, "jd.md", "# JD 内容", "JD", 0.9)
        ));
    }

    private void mockLlmOutput(String output) {
        mockLlmOutput(output, "# 简历");
    }

    private void mockLlmStreamOnly(String output) {
        doAnswer(invocation -> {
            StreamCallback cb = invocation.getArgument(1);
            cb.onToken(output);
            cb.onComplete(ChatResponse.builder().build());
            return null;
        }).when(llmClient).streamChat(any(ChatRequest.class), any(StreamCallback.class));
    }

    private void mockLlmOutput(String output, String savedMarkdown) {
        mockLlmStreamOnly(output);
        when(resumeVersionService.createVersion(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ResumeVersionVO(
                        "ver-1", "腾讯 - 算法", "WS-abc", "doc-1", "腾讯 算法",
                        savedMarkdown, List.of(), null, OffsetDateTime.now()
                ));
        when(workspaceSessionRepository.appendMessage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new AgentMessageEntity());
    }

    private static AgentSessionEntity jdSession() {
        AgentSessionEntity session = new AgentSessionEntity();
        session.setId(5L);
        session.setUserId(1L);
        session.setSessionId("WS-abc");
        session.setJdId("doc-1");
        session.setJdSnapshot("{\"company\":\"腾讯\",\"title\":\"算法工程师\"}");
        return session;
    }
}
