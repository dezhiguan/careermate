package com.careermate.agent.react;

import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.AgentToolExecutionService;
import com.careermate.agent.tool.AgentToolResult;
import com.careermate.llm.LlmClient;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReActEngineTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private AgentToolExecutionService toolExecutionService;

    private ReActEngine engine;
    private AgentToolContext toolContext;

    @BeforeEach
    void setUp() {
        engine = new ReActEngine(llmClient, toolExecutionService, new ObjectMapper());
        toolContext = AgentToolContext.builder()
                .userId(1L)
                .sessionId("S-001")
                .userMessage("请帮我详细分析岗位匹配差距")
                .build();
    }

    @Test
    void shortOrGreetingMessageSkipsReAct() {
        ReActTrace hello = engine.run(toolContext, "你好", "prompt");
        ReActTrace shortMsg = engine.run(toolContext, "短消息", "prompt");

        assertFalse(hello.hasSteps());
        assertFalse(shortMsg.hasSteps());
        assertFalse(hello.reachedFinalAnswer());
    }

    @Test
    void finalAnswerStopsImmediately() throws Exception {
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder()
                        .content("{\"thought\":\"信息足够\",\"action\":\"final_answer\"}")
                        .build()
        );

        ReActTrace trace = engine.run(toolContext, "请帮我详细分析岗位匹配差距和技能缺口", "prompt");

        assertTrue(trace.hasSteps());
        assertTrue(trace.reachedFinalAnswer());
        assertEquals(1, trace.rounds());
        assertEquals("final_answer", trace.steps().get(0).action());
    }

    @Test
    void toolCallThenFinalAnswer() throws Exception {
        when(llmClient.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .content("{\"thought\":\"先读简历\",\"action\":\"get_default_resume\"}")
                        .build())
                .thenReturn(ChatResponse.builder()
                        .content("{\"thought\":\"可以回答\",\"action\":\"final_answer\"}")
                        .build());
        when(toolExecutionService.execute(eq(toolContext), eq("get_default_resume")))
                .thenReturn(AgentToolResult.builder()
                        .toolName("get_default_resume")
                        .success(true)
                        .summary("已读取默认简历")
                        .build());

        ReActTrace trace = engine.run(toolContext, "请帮我详细分析岗位匹配差距和技能缺口", "prompt");

        assertTrue(trace.reachedFinalAnswer());
        assertEquals(2, trace.steps().size());
        assertEquals("get_default_resume", trace.steps().get(0).action());
        assertTrue(trace.steps().get(0).observation().contains("已读取"));
        verify(toolExecutionService, times(1)).execute(toolContext, "get_default_resume");
    }

    @Test
    void toolFailureUsesErrorObservation() throws Exception {
        when(llmClient.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .content("{\"thought\":\"读匹配\",\"action\":\"get_latest_job_match\"}")
                        .build())
                .thenReturn(ChatResponse.builder()
                        .content("{\"thought\":\"结束\",\"action\":\"final_answer\"}")
                        .build());
        when(toolExecutionService.execute(eq(toolContext), eq("get_latest_job_match")))
                .thenReturn(AgentToolResult.builder()
                        .toolName("get_latest_job_match")
                        .success(false)
                        .summary("失败")
                        .errorMessage("暂无记录")
                        .build());

        ReActTrace trace = engine.run(toolContext, "请帮我详细分析岗位匹配差距和技能缺口", "prompt");

        assertTrue(trace.steps().get(0).observation().contains("工具执行失败"));
        assertTrue(trace.steps().get(0).observation().contains("暂无记录"));
    }

    @Test
    void llmFailureReturnsPartialTrace() throws Exception {
        when(llmClient.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("LLM down"));

        ReActTrace trace = engine.run(toolContext, "请帮我详细分析岗位匹配差距和技能缺口", "prompt");

        assertFalse(trace.reachedFinalAnswer());
        assertFalse(trace.hasSteps());
    }

    @Test
    void invalidJsonStopsReasoning() throws Exception {
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder().content("plain text without json").build()
        );

        ReActTrace trace = engine.run(toolContext, "请帮我详细分析岗位匹配差距和技能缺口", "prompt");

        assertFalse(trace.hasSteps());
        assertFalse(trace.reachedFinalAnswer());
    }

    @Test
    void unknownToolTreatedAsFinalAnswer() throws Exception {
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(
                ChatResponse.builder()
                        .content("{\"thought\":\"乱猜工具\",\"action\":\"unknown_tool\"}")
                        .build()
        );

        ReActTrace trace = engine.run(toolContext, "请帮我详细分析岗位匹配差距和技能缺口", "prompt");

        assertTrue(trace.reachedFinalAnswer());
        assertEquals("final_answer", trace.steps().get(0).action());
    }
}
