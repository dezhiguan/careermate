package com.careermate.agent.memory.ltm;

import com.careermate.llm.LlmProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmbeddingAndVectorTest {

    @SuppressWarnings("unchecked")
    private ObjectProvider<StringRedisTemplate> noRedis() {
        ObjectProvider<StringRedisTemplate> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(null);
        return p;
    }

    private EmbeddingClient client(LtmProperties props, String llmKey) {
        LlmProperties llm = new LlmProperties();
        llm.setApiKey(llmKey);
        return new EmbeddingClient(props, llm, new ObjectMapper(), noRedis());
    }

    @Test
    void vectorFormat_rendersPgVector() {
        assertThat(VectorFormat.toPgVector(new float[]{0.1f, 0.2f, 0.3f})).isEqualTo("[0.1,0.2,0.3]");
        assertThat(VectorFormat.toPgVector(null)).isNull();
        assertThat(VectorFormat.toPgVector(new float[]{})).isNull();
    }

    @Test
    void embed_disabled_returnsEmpty() {
        LtmProperties props = new LtmProperties(); // enabled=false
        assertThat(client(props, "sk-key").embed("我只想远程")).isEmpty();
    }

    @Test
    void embed_enabledButNoKey_returnsEmpty() {
        LtmProperties props = new LtmProperties();
        props.setEnabled(true);
        assertThat(client(props, "").embed("我只想远程")).isEmpty();
    }

    @Test
    void embed_blankText_returnsEmpty() {
        LtmProperties props = new LtmProperties();
        props.setEnabled(true);
        assertThat(client(props, "sk-key").embed("  ")).isEmpty();
    }
}
