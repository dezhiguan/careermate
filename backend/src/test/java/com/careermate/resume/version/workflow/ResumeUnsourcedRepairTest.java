package com.careermate.resume.version.workflow;

import com.careermate.jobmatch.JobMatchAnalyzer;
import com.careermate.llm.LlmClient;
import com.careermate.llm.StreamCallback;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.model.entity.AgentMessageEntity;
import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.prompt.PromptRenderResult;
import com.careermate.prompt.PromptTemplateService;
import com.careermate.ragforge.RagForgeClient;
import com.careermate.ragforge.RagForgeChunk;
import com.careermate.resume.ResumeContext;
import com.careermate.resume.ResumeContextProvider;
import com.careermate.resume.version.service.ResumeVersionService;
import com.careermate.resume.version.verify.ResumeFactVerifier;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 事实校验未过时的定向修复。
 *
 * <p>靠提示词让模型「别编技术栈」是打地鼠：禁掉「具备快速补位 X 的基础」，它换成
 * 「具备 X 类框架落地基础」；线上三轮提示词加固把无出处项从 10 个压到 2 个就压不动了，
 * 而只要还剩一个，整份稿子就 SUSPECT、不落库，用户等四十秒只拿到一张确认卡片。
 *
 * <p>改法是把校验算出的<b>确切词表</b>回灌回去做定向删除——目标是确定的词而不是模糊的规则。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResumeUnsourcedRepairTest {

    @Mock private WorkspaceSessionRepository workspaceSessionRepository;
    @Mock private ResumeContextProvider resumeContextProvider;
    @Mock private RagForgeClient ragForgeClient;
    @Mock private LlmClient llmClient;
    @Mock private ResumeVersionService resumeVersionService;
    @Mock private PromptTemplateService promptTemplateService;
    @Mock private JobMatchAnalyzer jobMatchAnalyzer;
    @Mock private com.careermate.resume.coldstart.ColdStartResumeService coldStartResumeService;
    @Mock private com.careermate.agent.debate.ResumeCritic resumeCritic;
    @Mock private com.careermate.agent.session.AgentSessionService agentSessionService;

    private GenerateResumeWorkflowRunner runner;

    /** 源简历只有 Java/Go，没有 LangChain、RAG。 */
    private static final String SOURCE = "技术栈：Java / Go / SpringBoot / RocketMQ";

    @BeforeEach
    void setUp() {
        runner = new GenerateResumeWorkflowRunner(
                workspaceSessionRepository, resumeContextProvider, ragForgeClient, llmClient,
                resumeVersionService, new ObjectMapper(), promptTemplateService, jobMatchAnalyzer,
                new ResumeFactVerifier(), coldStartResumeService, resumeCritic,
                new com.careermate.agent.debate.DebateProperties());

        AgentSessionEntity session = new AgentSessionEntity();
        session.setId(5L);
        session.setUserId(1L);
        session.setSessionId("WS-abc");
        session.setJdId("doc-1");
        session.setJdSnapshot("{\"company\":\"腾讯\",\"title\":\"算法工程师\"}");
        when(workspaceSessionRepository.requireSession(1L, "WS-abc")).thenReturn(session);
        when(resumeContextProvider.getResumeContext(1L)).thenReturn(
                ResumeContext.builder().available(true).resumeId(10L).content(SOURCE).build());
        when(ragForgeClient.fetchDocumentChunks(1L)).thenReturn(List.of());
        when(ragForgeClient.searchJdByDocId(eq(1L), eq(50))).thenReturn(List.of(
                new RagForgeChunk(1L, 1L, "jd.md", "# JD 内容", "JD", 0.9)));
        when(promptTemplateService.render(any())).thenReturn(
                new PromptRenderResult("resume-generate-from-jd", "v5", "SYSTEM"));
        when(workspaceSessionRepository.appendMessage(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new AgentMessageEntity());
        when(resumeVersionService.createVersion(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new com.careermate.resume.version.dto.ResumeVersionVO(
                        "ver-1", "针对【腾讯】算法 · v1", "WS-abc", 1L, "腾讯 算法", "腾讯", "算法", 1,
                        "", "# 简历", List.of(), null, java.time.OffsetDateTime.now(), null, null));
    }

    /** 生成稿凭空写了 LangChain/RAG；修复删掉后校验通过，版本正常落库。 */
    @Test
    void 修复删掉无出处技术栈后版本得以落库() {
        stubStream("# 简历\n\n## 专业技能\nJava / Go，具备 LangChain 类框架落地基础，熟悉 RAG");
        stubRepair("# 简历\n\n## 专业技能\nJava / Go");

        execute();

        verify(resumeVersionService).createVersion(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    /** 修复没能清干净就保留改善后的版本，仍出确认卡片，绝不硬塞进库。 */
    @Test
    void 修复不彻底时仍走确认卡片() {
        stubStream("# 简历\n\n## 专业技能\nJava，具备 LangChain 基础，熟悉 RAG");
        stubRepair("# 简历\n\n## 专业技能\nJava，熟悉 RAG");

        execute();

        verify(resumeVersionService, never()).createVersion(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    /** 校验本来就通过时不该多花一次 LLM 往返。 */
    @Test
    void 校验通过时不触发修复() {
        AtomicInteger repairCalls = new AtomicInteger();
        stubStream("# 简历\n\n## 专业技能\nJava / Go");
        when(llmClient.chat(any(ChatRequest.class))).thenAnswer(inv -> {
            repairCalls.incrementAndGet();
            return ChatResponse.builder().content("# 简历").build();
        });

        execute();

        assertEquals(0, repairCalls.get(), "没有无出处项就不该再调一次 LLM");
    }

    /** 修复调用本身炸了也不能把已经生成好的稿子弄丢。 */
    @Test
    void 修复失败时保留原稿不中断流程() {
        stubStream("# 简历\n\n## 专业技能\nJava，具备 LangChain 基础");
        when(llmClient.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("LLM down"));

        execute();

        verify(workspaceSessionRepository).appendMessage(
                eq(1L), any(), eq("assistant"), any(), eq("CARD"), any(), eq(null));
    }

    private void execute() {
        runner.execute(new GenerateResumeWorkflowRun(1L, "WS-abc", "doc-1", null),
                new GenerateResumeWorkflowEventSink(agentSessionService, 1L, "WS-abc"));
    }

    private void stubStream(String output) {
        doAnswer(invocation -> {
            StreamCallback cb = invocation.getArgument(1);
            cb.onToken(output);
            cb.onComplete(ChatResponse.builder().build());
            return null;
        }).when(llmClient).streamChat(any(ChatRequest.class), any(StreamCallback.class));
    }

    private void stubRepair(String repaired) {
        when(llmClient.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content(repaired).build());
    }
}
