package com.careermate.agent.llm;

import com.careermate.llm.LlmClient;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrossFamilyLlmClientTest {

    private final LlmClient primary = mock(LlmClient.class);

    private SecondaryLlmProperties props(boolean enabled, String key) {
        SecondaryLlmProperties p = new SecondaryLlmProperties();
        p.setEnabled(enabled);
        p.setApiKey(key);
        return p;
    }

    @Test
    void disabled_usesPrimary() {
        CrossFamilyLlmClient c = new CrossFamilyLlmClient(primary, props(false, "sk-x"));
        when(primary.chat(any())).thenReturn(ChatResponse.builder().content("primary").build());

        assertThat(c.isCrossFamilyActive()).isFalse();
        assertThat(c.chat(ChatRequest.builder().build()).getContent()).isEqualTo("primary");
        verify(primary).chat(any());
    }

    @Test
    void enabledButNoKey_usesPrimary() {
        CrossFamilyLlmClient c = new CrossFamilyLlmClient(primary, props(true, ""));
        when(primary.chat(any())).thenReturn(ChatResponse.builder().content("primary").build());

        assertThat(c.isCrossFamilyActive()).isFalse();
        assertThat(c.chat(ChatRequest.builder().build()).getContent()).isEqualTo("primary");
        verify(primary).chat(any());
    }
}
