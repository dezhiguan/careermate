package com.careermate.llm;

import com.careermate.observability.LlmChatTraceRecorder;
import com.careermate.observability.LlmTracingSupport;
import com.careermate.observability.TracingLlmClient;
import com.careermate.llm.provider.DeepSeekLlmClient;
import com.careermate.llm.provider.MockLlmClient;
import com.careermate.llm.provider.OpenAiCompatibleLlmClient;
import com.careermate.llm.provider.QwenLlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LlmConfigTest {

    private final LlmConfig llmConfig = new LlmConfig();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private Tracer tracer;

    @Mock
    private LlmChatTraceRecorder llmChatTraceRecorder;

    private LlmTracingSupport llmTracingSupport;

    @BeforeEach
    void setUpTracer() {
        Span span = mock(Span.class);
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.tag(anyString(), anyString())).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(tracer.withSpan(any())).thenReturn(mock(Tracer.SpanInScope.class));
        llmTracingSupport = new LlmTracingSupport(tracer, baseProperties("mock"));
    }

    @Test
    void shouldCreateMockClientWhenProviderIsMock() {
        LlmProperties properties = baseProperties("mock");
        LlmClient client = llmConfig.llmClient(properties, objectMapper, llmTracingSupport, llmChatTraceRecorder);
        assertInstanceOf(TracingLlmClient.class, client);
        assertInstanceOf(MockLlmClient.class, unwrap(client));
    }

    @Test
    void shouldCreateDeepSeekClientWhenProviderIsDeepSeek() {
        LlmProperties properties = baseProperties("deepseek");
        assertInstanceOf(DeepSeekLlmClient.class, unwrap(llmConfig.llmClient(properties, objectMapper, llmTracingSupport, llmChatTraceRecorder)));
    }

    @Test
    void shouldCreateQwenClientWhenProviderIsQwen() {
        LlmProperties properties = baseProperties("qwen");
        assertInstanceOf(QwenLlmClient.class, unwrap(llmConfig.llmClient(properties, objectMapper, llmTracingSupport, llmChatTraceRecorder)));
    }

    @Test
    void shouldCreateOpenAiCompatibleClientWhenProviderIsOpenAiCompatible() {
        LlmProperties properties = baseProperties("openai-compatible");
        properties.setModel("gpt-4o-mini");
        properties.setEndpoint("https://example.openai-compatible.com/v1");
        assertInstanceOf(OpenAiCompatibleLlmClient.class, unwrap(llmConfig.llmClient(properties, objectMapper, llmTracingSupport, llmChatTraceRecorder)));
    }

    private LlmClient unwrap(LlmClient client) {
        assertInstanceOf(TracingLlmClient.class, client);
        try {
            var field = TracingLlmClient.class.getDeclaredField("delegate");
            field.setAccessible(true);
            return (LlmClient) field.get(client);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private LlmProperties baseProperties(String provider) {
        LlmProperties properties = new LlmProperties();
        properties.setProvider(provider);
        properties.setModel("mock-chat");
        properties.setApiKey("test-key");
        properties.setEndpoint("");
        return properties;
    }
}
