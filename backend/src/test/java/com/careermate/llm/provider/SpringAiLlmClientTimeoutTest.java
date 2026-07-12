package com.careermate.llm.provider;

import com.careermate.llm.LlmProperties;
import com.careermate.llm.StreamCallback;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * #5.1 回归：上游 LLM 挂起时，streamChat 应按 timeoutMs 超时返回（走 onError），
 * 而非 latch.await() 永久阻塞导致 agent-executor 线程泄漏。
 */
class SpringAiLlmClientTimeoutTest {

    @Test
    void streamChatTimesOutWhenUpstreamHangs() throws Exception {
        ChatModel hanging = mock(ChatModel.class);
        when(hanging.stream(any(Prompt.class))).thenReturn(Flux.never()); // 永不 complete/error

        LlmProperties props = new LlmProperties();
        props.setTimeoutMs(500L);
        SpringAiLlmClient client = new SpringAiLlmClient(props, "test", hanging);

        ChatRequest req = ChatRequest.builder()
                .messages(List.of(ChatMessage.builder().role("user").content("hi").build()))
                .build();

        AtomicReference<Throwable> err = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        StreamCallback cb = new StreamCallback() {
            @Override public void onToken(String token) { }
            @Override public void onComplete(ChatResponse response) { completed.set(true); }
            @Override public void onError(Throwable error) { err.set(error); }
        };

        ExecutorService ex = Executors.newSingleThreadExecutor();
        Future<?> future = ex.submit(() -> client.streamChat(req, cb));

        // 有超时：3s 内必然返回（timeoutMs=500ms），且走 onError 而非永久阻塞/onComplete
        assertDoesNotThrow(() -> future.get(3, TimeUnit.SECONDS),
                "streamChat 应在超时后返回，而非永久阻塞");
        assertNotNull(err.get(), "超时应触发 onError");
        assertFalse(completed.get(), "超时下不应触发 onComplete");

        ex.shutdownNow();
    }
}
