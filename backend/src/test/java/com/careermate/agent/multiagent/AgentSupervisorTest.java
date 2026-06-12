package com.careermate.agent.multiagent;

import com.careermate.agent.tool.AgentToolContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentSupervisorTest {

    @Mock
    private ResumeSpecialistAgent resumeAgent;
    @Mock
    private JobMatchSpecialistAgent jobMatchAgent;
    @Mock
    private InterviewSpecialistAgent interviewAgent;

    private AgentSupervisor supervisor;
    private AgentToolContext context;

    @BeforeEach
    void setUp() {
        supervisor = new AgentSupervisor(resumeAgent, jobMatchAgent, interviewAgent);
        context = AgentToolContext.builder()
                .userId(1L)
                .sessionId("S-001")
                .userMessage("test")
                .build();
    }

    @Test
    void generalMessageReturnsEmpty() {
        assertTrue(supervisor.dispatch(context, "今天天气怎么样").isEmpty());
        assertTrue(supervisor.dispatch(context, null).isEmpty());
        assertTrue(supervisor.dispatch(context, "   ").isEmpty());
    }

    @Test
    void resumeDomainDispatchesResumeAgent() {
        when(resumeAgent.process(context, "帮我优化简历项目描述"))
                .thenReturn(SpecialistResult.withTool(AgentDomain.RESUME, "get_default_resume", "已读取"));

        var results = supervisor.dispatch(context, "帮我优化简历项目描述");

        assertEquals(1, results.size());
        assertEquals("get_default_resume", results.get(0).toolName());
        verify(resumeAgent).process(context, "帮我优化简历项目描述");
    }

    @Test
    void jobMatchDomainDispatchesJobMatchAgent() {
        when(jobMatchAgent.process(context, "帮我分析岗位匹配和技能缺口"))
                .thenReturn(SpecialistResult.withTool(AgentDomain.JOB_MATCH, "get_latest_job_match", "匹配 80 分"));

        var results = supervisor.dispatch(context, "帮我分析岗位匹配和技能缺口");

        assertEquals(1, results.size());
        assertEquals(AgentDomain.JOB_MATCH, results.get(0).domain());
        verify(jobMatchAgent).process(context, "帮我分析岗位匹配和技能缺口");
    }

    @Test
    void interviewDomainDispatchesInterviewAgent() {
        when(interviewAgent.process(context, "帮我做面试模拟练习"))
                .thenReturn(SpecialistResult.withTool(AgentDomain.INTERVIEW, "create_interview_session", "已创建"));

        var results = supervisor.dispatch(context, "帮我做面试模拟练习");

        assertEquals(1, results.size());
        verify(interviewAgent).process(context, "帮我做面试模拟练习");
    }

    @Test
    void pdfGenerationRoutesToResumeDomain() {
        when(resumeAgent.process(any(), any()))
                .thenReturn(SpecialistResult.noTool(AgentDomain.RESUME));

        supervisor.dispatch(context, "请帮我生成 pdf 简历并下载");

        verify(resumeAgent).process(context, "请帮我生成 pdf 简历并下载");
    }
}
