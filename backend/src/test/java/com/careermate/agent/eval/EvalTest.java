package com.careermate.agent.eval;

import com.careermate.llm.LlmClient;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.mapper.EvalResultMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvalTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final com.careermate.agent.llm.CrossFamilyLlmClient llm =
            mock(com.careermate.agent.llm.CrossFamilyLlmClient.class);
    private final AnswerJudge judge = new AnswerJudge(llm, mapper);

    private ChatResponse resp(String c) {
        return ChatResponse.builder().content(c).build();
    }

    // ---- AnswerJudge ----

    @Test
    void judge_parsesFacetsIncludingCitation() {
        when(llm.chat(any(ChatRequest.class))).thenReturn(resp(
                "{\"relevance\":0.9,\"correctness\":0.8,\"citation_faithfulness\":0.2,\"overall\":0.6}"));
        EvalScore s = judge.judge("薪资多少", "大概很高", List.of("需分位数支撑"), null);
        assertThat(s.relevance()).isEqualTo(0.9);
        assertThat(s.citationFaithfulness()).isEqualTo(0.2);
        assertThat(s.overall()).isEqualTo(0.6);
    }

    @Test
    void judge_computesOverallWhenMissing_andClamps() {
        when(llm.chat(any(ChatRequest.class))).thenReturn(resp(
                "{\"relevance\":1.5,\"correctness\":1.0,\"citation_faithfulness\":1.0}"));
        EvalScore s = judge.judge("q", "a", List.of(), null);
        assertThat(s.relevance()).isEqualTo(1.0); // clamp
        assertThat(s.overall()).isEqualTo((1.0 + 1.0 + 1.0) / 3.0);
    }

    @Test
    void judge_neutralOnUnparseable() {
        when(llm.chat(any(ChatRequest.class))).thenReturn(resp("无法评分"));
        EvalScore s = judge.judge("q", "a", List.of(), null);
        assertThat(s.overall()).isEqualTo(0.5);
    }

    // ---- EvalRunner ----

    @Test
    void runner_scoresPersistsAndReports() {
        AnswerJudge fixedJudge = mock(AnswerJudge.class);
        when(fixedJudge.judge(any(), any(), any(), any()))
                .thenReturn(new EvalScore(0.9, 0.9, 0.9, 0.9), new EvalScore(0.3, 0.3, 0.3, 0.3));
        EvalResultMapper resultMapper = mock(EvalResultMapper.class);
        EvalRunner runner = new EvalRunner(fixedJudge, resultMapper, mapper);

        List<EvalScenario> scenarios = List.of(
                new EvalScenario("s1", "简历怎么改", List.of("覆盖JD"), "resume", false),
                new EvalScenario("s2", "薪资", List.of("分位数"), "salary", true));

        EvalReport report = runner.run("suite-1", scenarios, "qwen", "gpt-4o-mini", s -> "答案");

        assertThat(report.count()).isEqualTo(2);
        assertThat(report.avgOverall()).isEqualTo(0.6);
        assertThat(report.markdown()).contains("Eval 报告").contains("s1").contains("s2");
        verify(resultMapper, times(2)).insert(any(com.careermate.model.entity.EvalResultEntity.class));
    }

    @Test
    void percentile_lowTailAndEmpty() {
        assertThat(EvalRunner.percentile(List.of(), 95)).isEqualTo(0.0);
        assertThat(EvalRunner.percentile(List.of(0.1, 0.2, 0.3, 0.9), 95)).isEqualTo(0.9);
    }
}
