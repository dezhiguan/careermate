package com.careermate.agent.service;

import com.careermate.agent.config.AgentFrameworkProperties;
import com.careermate.llm.StreamCallback;
import com.careermate.llm.dto.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A1-1：把 LLM 流式调用从自研 {@code LlmClient} 迁到 Spring AI {@link ChatClient}。
 *
 * <p>用开关 {@code careermate.agent.framework.enabled} gate 住：开启且 ChatClient bean 存在时走
 * {@code ChatClient.prompt().stream()}，否则回退自研 LlmClient（保留兼容，A1-5 再删）。
 *
 * <p>把响应式 {@code Flux<String>} 桥接回既有 {@link StreamCallback} 契约：worker 线程阻塞在 latch，
 * 超时时由上层 interrupt worker 线程 → 抛 InterruptedException → dispose 订阅取消上游，语义与旧路径一致。
 */
@Slf4j
@Component
public class ChatClientStreamAdapter {

    private final ObjectProvider<ChatClient> chatClientProvider;
    private final AgentFrameworkProperties frameworkProperties;

    public ChatClientStreamAdapter(
            ObjectProvider<ChatClient> chatClientProvider,
            AgentFrameworkProperties frameworkProperties
    ) {
        this.chatClientProvider = chatClientProvider;
        this.frameworkProperties = frameworkProperties;
    }

    /**
     * 是否启用 Spring AI ChatClient 流式路径。要求开关开启且 ChatClient bean 实际存在。
     */
    public boolean isEnabled() {
        return frameworkProperties.isEnabled() && chatClientProvider.getIfAvailable() != null;
    }

    /**
     * 走 ChatClient 流式生成，把 token / 完成 / 错误事件回吐给既有 callback。
     * 阻塞当前（worker）线程直到流结束或被 interrupt。
     */
    public void stream(String systemPrompt, String userMessage, StreamCallback callback) {
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null) {
            callback.onError(new IllegalStateException("Spring AI ChatClient 未装配，无法走框架流式路径"));
            return;
        }

        StringBuilder accumulated = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Disposable subscription = chatClient.prompt()
                .system(systemPrompt == null ? "" : systemPrompt)
                .user(userMessage == null ? "" : userMessage)
                .stream()
                .content()
                .subscribe(
                        token -> {
                            if (token != null && !token.isEmpty()) {
                                accumulated.append(token);
                                callback.onToken(token);
                            }
                        },
                        error -> {
                            errorRef.set(error);
                            latch.countDown();
                        },
                        latch::countDown
                );

        try {
            latch.await();
        } catch (InterruptedException e) {
            subscription.dispose();
            Thread.currentThread().interrupt();
            callback.onError(new RuntimeException("cancelled"));
            return;
        }

        Throwable error = errorRef.get();
        if (error != null) {
            callback.onError(error);
            return;
        }
        callback.onComplete(ChatResponse.builder()
                .content(accumulated.toString())
                .model(frameworkProperties.getModel())
                .provider(frameworkProperties.getProvider())
                .build());
    }
}
