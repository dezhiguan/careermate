package com.careermate.agent.service;

import com.careermate.agent.config.AgentFrameworkProperties;
import com.careermate.llm.StreamCallback;
import com.careermate.llm.dto.ChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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

    @Test
    void isEnabled_false_whenFlagOff() {
        ChatClientStreamAdapter adapter =
                new ChatClientStreamAdapter(providerOf(mock(ChatClient.class)), props(false));
        assertThat(adapter.isEnabled()).isFalse();
    }

    @Test
    void isEnabled_false_whenFlagOnButNoBean() {
        ChatClientStreamAdapter adapter =
                new ChatClientStreamAdapter(providerOf(null), props(true));
        assertThat(adapter.isEnabled()).isFalse();
    }

    @Test
    void isEnabled_true_whenFlagOnAndBeanPresent() {
        ChatClientStreamAdapter adapter =
                new ChatClientStreamAdapter(providerOf(mock(ChatClient.class)), props(true));
        assertThat(adapter.isEnabled()).isTrue();
    }

    @Test
    void stream_forwardsTokensAndCompletesWithAccumulatedContent() {
        ChatClient client = mockChatClientEmitting(Flux.just("你好", "，世界"));
        ChatClientStreamAdapter adapter =
                new ChatClientStreamAdapter(providerOf(client), props(true));

        List<String> tokens = new ArrayList<>();
        AtomicReference<ChatResponse> completed = new AtomicReference<>();
        adapter.stream("system", "user", new StreamCallback() {
            @Override
            public void onToken(String token) {
                tokens.add(token);
            }

            @Override
            public void onComplete(ChatResponse response) {
                completed.set(response);
            }

            @Override
            public void onError(Throwable error) {
                throw new AssertionError("unexpected error", error);
            }
        });

        assertThat(tokens).containsExactly("你好", "，世界");
        assertThat(completed.get()).isNotNull();
        assertThat(completed.get().getContent()).isEqualTo("你好，世界");
        assertThat(completed.get().getModel()).isEqualTo("qwen-plus");
    }

    @Test
    void stream_skipsEmptyTokens() {
        ChatClient client = mockChatClientEmitting(Flux.just("", "a", ""));
        ChatClientStreamAdapter adapter =
                new ChatClientStreamAdapter(providerOf(client), props(true));

        List<String> tokens = new ArrayList<>();
        adapter.stream("s", "u", new StreamCallback() {
            @Override
            public void onToken(String token) {
                tokens.add(token);
            }

            @Override
            public void onComplete(ChatResponse response) {
            }

            @Override
            public void onError(Throwable error) {
            }
        });

        assertThat(tokens).containsExactly("a");
    }

    @Test
    void stream_onError_whenClientMissing() {
        ChatClientStreamAdapter adapter =
                new ChatClientStreamAdapter(providerOf(null), props(true));
        AtomicReference<Throwable> error = new AtomicReference<>();
        adapter.stream("s", "u", new StreamCallback() {
            @Override
            public void onToken(String token) {
            }

            @Override
            public void onComplete(ChatResponse response) {
            }

            @Override
            public void onError(Throwable e) {
                error.set(e);
            }
        });
        assertThat(error.get()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void stream_propagatesUpstreamError() {
        ChatClient client = mockChatClientEmitting(Flux.error(new RuntimeException("boom")));
        ChatClientStreamAdapter adapter =
                new ChatClientStreamAdapter(providerOf(client), props(true));

        AtomicReference<Throwable> error = new AtomicReference<>();
        adapter.stream("s", "u", new StreamCallback() {
            @Override
            public void onToken(String token) {
            }

            @Override
            public void onComplete(ChatResponse response) {
                throw new AssertionError("should not complete");
            }

            @Override
            public void onError(Throwable e) {
                error.set(e);
            }
        });
        assertThat(error.get()).hasMessage("boom");
    }

    private ChatClient mockChatClientEmitting(Flux<String> content) {
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamSpec = mock(ChatClient.StreamResponseSpec.class);
        when(client.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(content);
        return client;
    }
}
