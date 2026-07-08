package com.careermate.llm.provider;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.careermate.llm.LlmClient;
import com.careermate.llm.LlmProperties;
import com.careermate.llm.StreamCallback;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.llm.dto.ToolCallRequest;
import com.careermate.llm.dto.ToolCallResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A1-5：Spring AI ChatModel 支撑的 {@link LlmClient} 实现，替代自研 HTTP provider（Qwen/DeepSeek/OpenAiCompatible）。
 * 保留稳定的 LlmClient 接口 + DTO，消费方零改动；底层统一走 Spring AI（DashScope / OpenAI 兼容）。
 */
@Slf4j
public class SpringAiLlmClient implements LlmClient {

    public static final String PROVIDER_DASHSCOPE = "spring-ai-dashscope";
    public static final String PROVIDER_OPENAI = "spring-ai-openai";

    private final LlmProperties properties;
    private final ChatModel chatModel;
    private final String providerName;

    public SpringAiLlmClient(LlmProperties properties, String providerName) {
        this(properties, providerName, buildChatModel(properties, providerName));
    }

    /** 可注入 ChatModel（测试用）。 */
    SpringAiLlmClient(LlmProperties properties, String providerName, ChatModel chatModel) {
        this.properties = properties;
        this.providerName = providerName;
        this.chatModel = chatModel;
    }

    private static ChatModel buildChatModel(LlmProperties p, String provider) {
        if (PROVIDER_OPENAI.equals(provider)) {
            OpenAiApi.Builder api = OpenAiApi.builder().apiKey(p.getApiKey() == null ? "" : p.getApiKey());
            if (StringUtils.hasText(p.getEndpoint())) {
                api.baseUrl(p.getEndpoint().trim());
            }
            return OpenAiChatModel.builder()
                    .openAiApi(api.build())
                    .defaultOptions(OpenAiChatOptions.builder().model(p.getModel()).build())
                    .build();
        }
        // 默认 DashScope
        return DashScopeChatModel.builder()
                .dashScopeApi(DashScopeApi.builder().apiKey(p.getApiKey() == null ? "" : p.getApiKey()).build())
                .defaultOptions(DashScopeChatOptions.builder().withModel(p.getModel()).build())
                .build();
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        long start = System.currentTimeMillis();
        org.springframework.ai.chat.model.ChatResponse resp = chatModel.call(toPrompt(request));
        String content = resp != null && resp.getResult() != null && resp.getResult().getOutput() != null
                ? resp.getResult().getOutput().getText() : "";
        ChatResponse out = ChatResponse.builder()
                .content(content == null ? "" : content)
                .model(request.getModel() != null ? request.getModel() : properties.getModel())
                .provider(providerName)
                .latencyMs(System.currentTimeMillis() - start)
                .build();
        applyUsage(out, resp);
        return out;
    }

    @Override
    public void streamChat(ChatRequest request, StreamCallback callback) {
        StringBuilder acc = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> err = new AtomicReference<>();
        AtomicReference<org.springframework.ai.chat.model.ChatResponse> lastResp = new AtomicReference<>();

        Disposable sub = chatModel.stream(toPrompt(request)).subscribe(
                chunk -> {
                    lastResp.set(chunk);
                    String token = chunk != null && chunk.getResult() != null && chunk.getResult().getOutput() != null
                            ? chunk.getResult().getOutput().getText() : null;
                    if (StringUtils.hasText(token)) {
                        acc.append(token);
                        callback.onToken(token);
                    }
                },
                error -> {
                    err.set(error);
                    latch.countDown();
                },
                latch::countDown);

        try {
            latch.await();
        } catch (InterruptedException e) {
            sub.dispose();
            Thread.currentThread().interrupt();
            callback.onError(new RuntimeException("cancelled"));
            return;
        }
        if (err.get() != null) {
            callback.onError(err.get());
            return;
        }
        ChatResponse out = ChatResponse.builder()
                .content(acc.toString())
                .model(request.getModel() != null ? request.getModel() : properties.getModel())
                .provider(providerName)
                .build();
        applyUsage(out, lastResp.get());
        callback.onComplete(out);
    }

    @Override
    public ToolCallResponse toolCall(ToolCallRequest request) {
        // 未被任何消费方直接调用；工具调用统一走 Spring AI function-calling（A1-2）。
        throw new UnsupportedOperationException("toolCall 已由 Spring AI function-calling 取代");
    }

    private Prompt toPrompt(ChatRequest request) {
        List<Message> messages = new ArrayList<>();
        if (request.getMessages() != null) {
            for (com.careermate.llm.dto.ChatMessage m : request.getMessages()) {
                String role = m.getRole() == null ? "user" : m.getRole().toLowerCase();
                String content = m.getContent() == null ? "" : m.getContent();
                switch (role) {
                    case "system" -> messages.add(new SystemMessage(content));
                    case "assistant", "agent" -> messages.add(new AssistantMessage(content));
                    default -> messages.add(new UserMessage(content));
                }
            }
        }
        return new Prompt(messages, buildOptions(request));
    }

    private org.springframework.ai.chat.prompt.ChatOptions buildOptions(ChatRequest request) {
        String model = StringUtils.hasText(request.getModel()) ? request.getModel() : properties.getModel();
        Double temperature = request.getTemperature() != null ? request.getTemperature() : properties.getTemperature();
        if (PROVIDER_OPENAI.equals(providerName)) {
            return OpenAiChatOptions.builder().model(model).temperature(temperature).build();
        }
        return DashScopeChatOptions.builder().withModel(model).withTemperature(temperature).build();
    }

    private void applyUsage(ChatResponse out, org.springframework.ai.chat.model.ChatResponse resp) {
        try {
            if (resp != null && resp.getMetadata() != null && resp.getMetadata().getUsage() != null) {
                out.setInputTokens(resp.getMetadata().getUsage().getPromptTokens());
                out.setOutputTokens(resp.getMetadata().getUsage().getCompletionTokens());
            }
        } catch (Exception ignored) {
            // 用量缺失不影响
        }
    }
}
