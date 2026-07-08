package com.careermate.llm.provider;

import com.careermate.llm.LlmProperties;
import com.careermate.llm.StreamCallback;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.llm.dto.ToolCallRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringAiLlmClientTest {

    private LlmProperties props() {
        LlmProperties p = new LlmProperties();
        p.setModel("qwen-plus");
        p.setTemperature(0.7);
        return p;
    }

    private org.springframework.ai.chat.model.ChatResponse springResp(String text) {
        return new org.springframework.ai.chat.model.ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    void chat_returnsContent() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(springResp("你好，世界"));
        SpringAiLlmClient client = new SpringAiLlmClient(props(), SpringAiLlmClient.PROVIDER_DASHSCOPE, model);

        ChatResponse resp = client.chat(ChatRequest.builder()
                .messages(List.of(ChatMessage.builder().role("user").content("hi").build()))
                .build());

        assertThat(resp.getContent()).isEqualTo("你好，世界");
        assertThat(resp.getProvider()).isEqualTo(SpringAiLlmClient.PROVIDER_DASHSCOPE);
    }

    @Test
    void streamChat_forwardsTokensAndCompletes() {
        ChatModel model = mock(ChatModel.class);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(springResp("你好"), springResp("，世界")));
        SpringAiLlmClient client = new SpringAiLlmClient(props(), SpringAiLlmClient.PROVIDER_DASHSCOPE, model);

        List<String> tokens = new ArrayList<>();
        AtomicReference<ChatResponse> done = new AtomicReference<>();
        client.streamChat(ChatRequest.builder()
                        .messages(List.of(ChatMessage.builder().role("user").content("hi").build())).build(),
                new StreamCallback() {
                    @Override
                    public void onToken(String token) {
                        tokens.add(token);
                    }

                    @Override
                    public void onComplete(ChatResponse response) {
                        done.set(response);
                    }

                    @Override
                    public void onError(Throwable error) {
                        throw new AssertionError(error);
                    }
                });

        assertThat(tokens).containsExactly("你好", "，世界");
        assertThat(done.get().getContent()).isEqualTo("你好，世界");
    }

    @Test
    void streamChat_propagatesError() {
        ChatModel model = mock(ChatModel.class);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.error(new RuntimeException("boom")));
        SpringAiLlmClient client = new SpringAiLlmClient(props(), SpringAiLlmClient.PROVIDER_DASHSCOPE, model);

        AtomicReference<Throwable> err = new AtomicReference<>();
        client.streamChat(ChatRequest.builder()
                        .messages(List.of(ChatMessage.builder().role("user").content("hi").build())).build(),
                new StreamCallback() {
                    @Override public void onToken(String token) { }
                    @Override public void onComplete(ChatResponse response) {
                        throw new AssertionError("should not complete");
                    }
                    @Override public void onError(Throwable error) {
                        err.set(error);
                    }
                });

        assertThat(err.get()).hasMessage("boom");
    }

    @Test
    void toolCall_unsupported() {
        SpringAiLlmClient client = new SpringAiLlmClient(props(), SpringAiLlmClient.PROVIDER_DASHSCOPE, mock(ChatModel.class));
        assertThatThrownBy(() -> client.toolCall(new ToolCallRequest()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
