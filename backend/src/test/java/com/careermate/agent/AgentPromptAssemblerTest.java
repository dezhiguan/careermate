package com.careermate.agent;

import com.careermate.agent.context.CareerProfileContextResult;
import com.careermate.agent.context.ConversationContextResult;
import com.careermate.agent.multiagent.AgentDomain;
import com.careermate.agent.multiagent.SpecialistResult;
import com.careermate.agent.react.ReActStep;
import com.careermate.agent.react.ReActTrace;
import com.careermate.agent.tool.AgentToolResult;
import com.careermate.jobmatch.JobMatchContext;
import com.careermate.resume.ResumeContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPromptAssemblerTest {

    @Test
    void buildBaseSystemPromptContainsCareerMateIdentity() {
        String prompt = AgentPromptAssembler.buildBaseSystemPrompt();
        assertTrue(prompt.contains("CareerMate"));
        assertTrue(prompt.contains("小职"));
    }

    @Test
    void buildSystemPromptAppendsResumeAndJobMatch() {
        ResumeContext resume = ResumeContext.builder()
                .available(true)
                .contextText("用户默认简历：\n标题：Java 后端")
                .build();
        JobMatchContext jobMatch = JobMatchContext.builder()
                .available(true)
                .contextText("最近岗位匹配结果：\n岗位：Java 工程师")
                .build();

        String prompt = AgentPromptAssembler.buildSystemPrompt(resume, jobMatch);

        assertTrue(prompt.contains("CareerMate"));
        assertTrue(prompt.contains("Java 后端"));
        assertTrue(prompt.contains("Java 工程师"));
    }

    @Test
    void appendMethodsSkipNullOrEmptyContext() {
        String base = "base";
        assertEquals(base, AgentPromptAssembler.appendCareerProfileContext(base, null));
        assertEquals(base, AgentPromptAssembler.appendResumeContext(base, null));
        assertEquals(base, AgentPromptAssembler.appendJobMatchContext(base, null));
        assertEquals(base, AgentPromptAssembler.appendConversationContext(base, null));
        assertEquals(base, AgentPromptAssembler.appendToolResult(base, null));
        assertEquals(base, AgentPromptAssembler.appendSpecialistResult(base, null));
        assertEquals(base, AgentPromptAssembler.appendReActTrace(base, null));
    }

    @Test
    void appendCareerProfileContextAddsText() {
        CareerProfileContextResult profile = CareerProfileContextResult.builder()
                .available(true)
                .contextText("【用户求职画像】\n目标岗位：Java 后端")
                .targetRole("Java 后端")
                .skillCount(3)
                .build();

        String prompt = AgentPromptAssembler.appendCareerProfileContext("base", profile);

        assertTrue(prompt.contains("【用户求职画像】"));
        assertTrue(prompt.contains("Java 后端"));
    }

    @Test
    void appendConversationContextAddsHistory() {
        ConversationContextResult conversation = ConversationContextResult.builder()
                .available(true)
                .contextText("【当前会话历史】\nuser: 你好")
                .messageCount(1)
                .charCount(10)
                .loadFailed(false)
                .build();

        String prompt = AgentPromptAssembler.appendConversationContext("base", conversation);

        assertTrue(prompt.contains("【当前会话历史】"));
    }

    @Test
    void appendToolResultIncludesStructuredDataAndError() {
        AgentToolResult success = AgentToolResult.builder()
                .toolName("get_default_resume")
                .success(true)
                .summary("已读取默认简历")
                .data(Map.of("title", "后端简历"))
                .build();
        AgentToolResult failure = AgentToolResult.builder()
                .toolName("create_job_match")
                .success(false)
                .summary("匹配失败")
                .errorMessage("缺少 JD")
                .build();

        String successPrompt = AgentPromptAssembler.appendToolResult("base", success);
        String failurePrompt = AgentPromptAssembler.appendToolResult("base", failure);

        assertTrue(successPrompt.contains("get_default_resume"));
        assertTrue(successPrompt.contains("后端简历"));
        assertTrue(failurePrompt.contains("错误：缺少 JD"));
    }

    @Test
    void appendSpecialistResultAddsDomainSummary() {
        SpecialistResult specialist = SpecialistResult.withTool(
                AgentDomain.RESUME,
                "get_default_resume",
                "已读取简历"
        );

        String prompt = AgentPromptAssembler.appendSpecialistResult("base", specialist);

        assertTrue(prompt.contains("RESUME"));
        assertTrue(prompt.contains("已读取简历"));
        assertTrue(prompt.contains("状态：SUCCESS"));
    }

    @Test
    void appendSpecialistResultDoesNotLeakSensitiveStructuredData() {
        SpecialistResult specialist = SpecialistResult.builder()
                .domain(AgentDomain.MARKET)
                .agentName("MarketSpecialistAgent")
                .summary("市场参考")
                .status(com.careermate.agent.multiagent.SpecialistResultStatus.SUCCESS)
                .structuredData(java.util.Map.of(
                        "content", "完整敏感内容不应出现",
                        "jdContent", "完整JD",
                        "chunkCount", 2,
                        "scene", "MARKET"
                ))
                .build();

        String prompt = AgentPromptAssembler.appendSpecialistResult("base", specialist);

        assertTrue(prompt.contains("chunkCount"));
        assertTrue(prompt.contains("scene"));
        assertFalse(prompt.contains("完整敏感内容不应出现"));
        assertFalse(prompt.contains("完整JD"));
        assertTrue(prompt.contains("contentLength"));
    }

    @Test
    void criticBlockedResultAddsConstraintToPrompt() {
        SpecialistResult specialist = SpecialistResult.builder()
                .domain(AgentDomain.CRITIC)
                .agentName("CriticAgent")
                .summary("不能编造经历")
                .status(com.careermate.agent.multiagent.SpecialistResultStatus.BLOCKED)
                .riskLevel(com.careermate.agent.multiagent.SpecialistRiskLevel.HIGH)
                .warnings(java.util.List.of("不得执行写简历工具"))
                .build();

        String prompt = AgentPromptAssembler.appendSpecialistResult("base", specialist);

        assertTrue(prompt.contains("BLOCKED"));
        assertTrue(prompt.contains("不得执行会写入或编造虚假经历的工具"));
        assertTrue(AgentPromptAssembler.shouldAppendSpecialistResult(specialist));
    }

    @Test
    void appendReActTraceAddsReasoningChain() {
        ReActTrace trace = new ReActTrace(
                List.of(new ReActStep(1, "需要读简历", "get_default_resume", "已读取")),
                true,
                1
        );

        String prompt = AgentPromptAssembler.appendReActTrace("base", trace);

        assertTrue(prompt.contains("【ReAct 推理链】"));
        assertTrue(prompt.contains("get_default_resume"));
    }

    @Test
    void appendWorkspaceContextForJdPrepSpace() {
        String snapshot = "{\"company\":\"腾讯\",\"title\":\"Java 后端\"}";

        String prompt = AgentPromptAssembler.appendWorkspaceContext(
                "base",
                "JD_PREP",
                "jd-001",
                snapshot
        );

        assertTrue(prompt.contains("JD 准备空间"));
        assertTrue(prompt.contains("jd-001"));
        assertTrue(prompt.contains("腾讯"));
        assertTrue(prompt.contains("Java 后端"));
        assertTrue(prompt.contains("generate_resume_from_jd"));
    }

    @Test
    void appendWorkspaceContextSkipsNonJdPrep() {
        assertEquals("base", AgentPromptAssembler.appendWorkspaceContext("base", "CHAT", "jd-1", "{}"));
        assertEquals("base", AgentPromptAssembler.appendWorkspaceContext("base", "JD_PREP", "", "{}"));
    }

    @Test
    void appendWorkspaceContextIgnoresInvalidSnapshotJson() {
        String prompt = AgentPromptAssembler.appendWorkspaceContext(
                "base",
                "JD_PREP",
                "jd-002",
                "not-json"
        );

        assertTrue(prompt.contains("jd-002"));
        assertFalse(prompt.contains("not-json"));
    }
}
