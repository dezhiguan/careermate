package com.careermate.agent.debate;

import com.careermate.llm.LlmClient;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.mapper.AgentCollabMessageMapper;
import com.careermate.mapper.AgentCollabSessionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DebateTest {

    private final ObjectMapper om = new ObjectMapper();
    private final ResumeWriter writer = mock(ResumeWriter.class);
    private final ResumeCritic critic = mock(ResumeCritic.class);
    private final AgentCollabSessionMapper sessionMapper = mock(AgentCollabSessionMapper.class);
    private final AgentCollabMessageMapper messageMapper = mock(AgentCollabMessageMapper.class);

    private WriterCriticDebateEngine engine(DebateProperties props) {
        when(writer.write(anyString(), anyString())).thenReturn("初稿");
        when(writer.revise(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn("改稿");
        return new WriterCriticDebateEngine(writer, critic, props, sessionMapper, messageMapper, om);
    }

    private DebateProperties props(int maxRounds, double threshold) {
        DebateProperties p = new DebateProperties();
        p.setEnabled(true);
        p.setMaxRounds(maxRounds);
        p.setConsensusThreshold(threshold);
        return p;
    }

    @Test
    void consensusFirstRound_noRevision() {
        when(critic.review(anyString(), anyString())).thenReturn(new CriticVerdict(0.9, "c", "s"));
        DebateResult r = engine(props(3, 0.85)).debate("jd", "resume");

        assertThat(r.status()).isEqualTo("CONSENSUS");
        assertThat(r.rounds()).isEqualTo(1);
        assertThat(r.finalDraft()).isEqualTo("初稿");
        verify(writer, never()).revise(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(sessionMapper).insert(any(com.careermate.model.entity.AgentCollabSessionEntity.class));
    }

    @Test
    void revisesThenConsensus() {
        when(critic.review(anyString(), anyString()))
                .thenReturn(new CriticVerdict(0.5, "缺关键词", "补Spring Cloud"), new CriticVerdict(0.9, "好", "无"));
        DebateResult r = engine(props(3, 0.85)).debate("jd", "resume");

        assertThat(r.status()).isEqualTo("CONSENSUS");
        assertThat(r.rounds()).isEqualTo(2);
        assertThat(r.finalDraft()).isEqualTo("改稿");
        // writer 改稿只收到 criticism/suggestion，不含 satisfaction
        verify(writer).revise("jd", "resume", "初稿", "缺关键词", "补Spring Cloud");
    }

    @Test
    void maxRoundsWhenNeverSatisfied() {
        when(critic.review(anyString(), anyString())).thenReturn(new CriticVerdict(0.4, "c", "s"));
        DebateResult r = engine(props(3, 0.85)).debate("jd", "resume");

        assertThat(r.status()).isEqualTo("MAX_ROUND");
        assertThat(r.rounds()).isEqualTo(3);
        // 3 轮：仅前 2 轮改稿（最后一轮不再改）
        verify(writer, times(2)).revise(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void hardCapsRoundsAtFive() {
        when(critic.review(anyString(), anyString())).thenReturn(new CriticVerdict(0.1, "c", "s"));
        DebateResult r = engine(props(10, 0.85)).debate("jd", "resume"); // 请求 10 → 封顶 5
        assertThat(r.rounds()).isEqualTo(5);
    }

    // ---- ResumeCritic ----

    @Test
    void critic_parsesJson() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .content("{\"satisfaction\":0.7,\"criticism\":\"量化不足\",\"suggestion\":\"加数字\"}").build());
        ResumeCritic c = new ResumeCritic(llm, om, props(3, 0.85));
        CriticVerdict v = c.review("jd", "draft");
        assertThat(v.satisfaction()).isEqualTo(0.7);
        assertThat(v.criticism()).isEqualTo("量化不足");
    }

    @Test
    void critic_neutralOnGarbage() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("无法评审").build());
        CriticVerdict v = new ResumeCritic(llm, om, props(3, 0.85)).review("jd", "draft");
        assertThat(v.satisfaction()).isEqualTo(0.5);
    }

    // ---- ResumeWriter ----

    @Test
    void writer_returnsContentAndEmptyOnFailure() {
        LlmClient llm = mock(LlmClient.class);
        when(llm.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("定制简历正文").build());
        ResumeWriter w = new ResumeWriter(llm, props(3, 0.85));
        assertThat(w.write("jd", "resume")).isEqualTo("定制简历正文");

        when(llm.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("down"));
        assertThat(w.write("jd", "resume")).isEmpty();
    }
}
