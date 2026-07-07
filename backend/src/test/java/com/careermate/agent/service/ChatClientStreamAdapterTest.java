package com.careermate.agent.service;

import com.careermate.agent.config.AgentFrameworkProperties;
import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.springai.SpringAiToolCallbackFactory;
import com.careermate.llm.StreamCallback;
import com.careermate.llm.dto.ChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatClientStreamAdapterTest {

    @SuppressWarnings("unchecked")
    private ObjectProvider<ChatClient> providerOf(ChatClient client) {
        ObjectProvider<ChatClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        return provider;
    }

    private AgentFrameworkProperties props(boolean enabled) {
        AgentFrameworkProperties p = new AgentFrameworkProperties();
        p.setEnabled(enabled);
        p.setModel("qwen-plus");
        p.setProvider("spring-ai-alibaba-dashscope");
        return p;
    }

    private ChatClientStreamAdapter adapter(ChatClient client, boolean enabled, SpringAiToolCallbackFactory factory) {
        return new ChatClientStreamAdapter(providerOf(client), props(enabled), factory);
    }

    private ChatClientStreamAdapter adapter(ChatClient client, boolean enabled) {
        return adapter(client, enabled, mock(SpringAiToolCallbackFactory.class));
    }

    private StreamCallback collecting(List<String> tokens, AtomicReference<ChatResponse> completed,
                                      AtomicReference<Throwable> error) {
        return new StreamCallback() {
            @Override
            public void onToken(String token) {
                if (tokens != null) tokens.add(token);
            }

            @Override
            public void onComplete(ChatResponse response) {
                if (completed != null) completed.set(response);
            }

            @Override
            public void onError(Throwable e) {
                if (error != null) error.set(e);
            }
        };
    }

    @Test
    void isEnabled_false_whenFlagOff() {
        assertThat(adapter(mock(ChatClient.class), false).isEnabled()).isFalse();
    }

    @Test
    void isEnabled_false_whenFlagOnButNoBean() {
        assertThat(adapter(null, true).isEnabled()).isFalse();
    }

    @Test
    void isEnabled_true_whenFlagOnAndBeanPresent() {
        assertThat(adapter(mock(ChatClient.class), true).isEnabled()).isTrue();
    }

    @Test
    void stream_forwardsTokensAndCompletesWithAccumulatedContent() {
        ChatClient client = mockChatClientEmitting(Flux.just("你好", "，世界"));
        List<String> tokens = new ArrayList<>();
        AtomicReference<ChatResponse> completed = new AtomicReference<>();

        adapter(client, true).stream("system", "user", null, collecting(tokens, completed, null));

        assertThat(tokens).containsExactly("你好", "，世界");
        assertThat(completed.get()).isNotNull();
        assertThat(completed.get().getContent()).isEqualTo("你好，世界");
        assertThat(completed.get().getModel()).isEqualTo("qwen-plus");
    }

    @Test
    void stream_skipsEmptyTokens() {
        ChatClient client = mockChatClientEmitting(Flux.just("", "a", ""));
        List<String> tokens = new ArrayList<>();

        adapter(client, true).stream("s", "u", null, collecting(tokens, null, null));

        assertThat(tokens).containsExactly("a");
    }

    @Test
    void stream_onError_whenClientMissing() {
        AtomicReference<Throwable> error = new AtomicReference<>();
        adapter(null, true).stream("s", "u", null, collecting(null, null, error));
        assertThat(error.get()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void stream_propagatesUpstreamError() {
        ChatClient client = mockChatClientEmitting(Flux.error(new RuntimeException("boom")));
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<ChatResponse> completed = new AtomicReference<>();

        adapter(client, true).stream("s", "u", null, collecting(null, completed, error));

        assertThat(completed.get()).isNull();
        assertThat(error.get()).hasMessage("boom");
    }

    @Test
    void stream_attachesToolCallbacks_whenToolContextHasUserId() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient client = mockChatClientEmitting(requestSpec, Flux.just("ok"));

        SpringAiToolCallbackFactory factory = mock(SpringAiToolCallbackFactory.class);
        List<ToolCallback> callbacks = List.of(mock(ToolCallback.class));
        when(factory.createCallbacks(any(AgentToolContext.class))).thenReturn(callbacks);

        AgentToolContext ctx = AgentToolContext.builder().userId(7L).sessionId("s1").userMessage("hi").build();
        adapter(client, true, factory).stream("sys", "hi", ctx, collecting(null, null, null));

        verify(factory).createCallbacks(any(AgentToolContext.class));
        verify(requestSpec).toolCallbacks(callbacks);
    }

    @Test
    void stream_skipsToolCallbacks_whenNoUserId() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient client = mockChatClientEmitting(requestSpec, Flux.just("ok"));
        SpringAiToolCallbackFactory factory = mock(SpringAiToolCallbackFactory.class);

        adapter(client, true, factory).stream("sys", "hi", null, collecting(null, null, null));

        verify(factory, never()).createCallbacks(any());
        verify(requestSpec, never()).toolCallbacks(any(List.class));
    }

    private ChatClient mockChatClientEmitting(Flux<String> content) {
        return mockChatClientEmitting(mock(ChatClient.ChatClientRequestSpec.class), content);
    }

    private ChatClient mockChatClientEmitting(ChatClient.ChatClientRequestSpec requestSpec, Flux<String> content) {
        ChatClient client = mock(ChatClient.class);
        ChatClient.StreamResponseSpec streamSpec = mock(ChatClient.StreamResponseSpec.class);
        when(client.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.toolCallbacks(any(List.class))).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(content);
        return client;
    }
}
