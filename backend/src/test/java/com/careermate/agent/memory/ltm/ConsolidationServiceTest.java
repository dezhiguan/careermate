package com.careermate.agent.memory.ltm;

import com.careermate.llm.LlmClient;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsolidationServiceTest {

    private final LlmClient llm = mock(LlmClient.class);
    private final LongTermMemoryService ltm = mock(LongTermMemoryService.class);
    private final ConsolidationService service = new ConsolidationService(llm, new ObjectMapper(), ltm);

    private ChatResponse resp(String c) {
        return ChatResponse.builder().content(c).build();
    }

    private List<String> msgs(int n) {
        return java.util.stream.IntStream.range(0, n).mapToObj(i -> "user: 消息" + i).toList();
    }

    @Test
    void tooFewMessages_skips() {
        assertThat(service.consolidate(7L, msgs(3))).isZero();
        verify(llm, never()).chat(any());
    }

    @Test
    void distillsValidFactsAndStores() {
        when(llm.chat(any(ChatRequest.class))).thenReturn(resp("""
                {"facts":[
                  {"fact_type":"PREFERENCE","fact_text":"只考虑远程","confidence":0.8},
                  {"fact_type":"skill","fact_text":"精通Java","confidence":0.7},
                  {"fact_type":"BOGUS","fact_text":"忽略我","confidence":0.9},
                  {"fact_type":"GOAL","fact_text":"","confidence":0.5}
                ]}"""));
        when(ltm.store(eq(7L), any(), any(), anyDouble())).thenReturn(true);

        int stored = service.consolidate(7L, msgs(6));

        // 有效：PREFERENCE + skill(归一化 SKILL)；BOGUS 类型无效、GOAL 空文本被过滤
        assertThat(stored).isEqualTo(2);
        verify(ltm).store(7L, "PREFERENCE", "只考虑远程", 0.8);
        verify(ltm).store(7L, "SKILL", "精通Java", 0.7);
    }

    @Test
    void unparseable_storesNothing() {
        when(llm.chat(any(ChatRequest.class))).thenReturn(resp("我不会"));
        assertThat(service.consolidate(7L, msgs(6))).isZero();
    }

    @Test
    void llmThrows_returnsZeroGracefully() {
        when(llm.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("down"));
        assertThat(service.consolidate(7L, msgs(6))).isZero();
    }
}
