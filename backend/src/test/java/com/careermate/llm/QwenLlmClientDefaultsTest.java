package com.careermate.llm;

import com.careermate.llm.provider.LlmProviderDefaults;
import com.careermate.llm.provider.QwenLlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class QwenLlmClientDefaultsTest {

    @Test
    void appliesDashScopeDefaultsWhenEndpointAndModelMissing() {
        LlmProperties source = new LlmProperties();
        source.setProvider("qwen");
        source.setApiKey("test-key");
        source.setEndpoint("");
        source.setModel("mock-chat");

        LlmProperties resolved = LlmProviderDefaults.copyWithQwenDefaults(source);
        assertEquals(LlmProviderDefaults.QWEN_ENDPOINT, resolved.getEndpoint());
        assertEquals(LlmProviderDefaults.QWEN_MODEL, resolved.getModel());

        assertInstanceOf(QwenLlmClient.class, new LlmConfig().llmClient(source, new ObjectMapper()));
    }
}
