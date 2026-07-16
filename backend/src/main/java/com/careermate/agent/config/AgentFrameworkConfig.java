package com.careermate.agent.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnProperty(prefix = "careermate.agent.framework", name = "enabled", havingValue = "true")
public class AgentFrameworkConfig {

    public static final String CHAT_MODEL_BEAN = "agentFrameworkChatModel";
    public static final String CHAT_CLIENT_BEAN = "agentFrameworkChatClient";
    public static final String CHAT_MEMORY_BEAN = "agentFrameworkChatMemory";

    /** 兜底默认：配置缺失时的对话记忆窗口上限（M4：由 20 提高，避免机会主线聊过 20 条即失忆）。 */
    private static final int DEFAULT_CHAT_MEMORY_MAX_MESSAGES = 100;

    private static final String PROVIDER_OPENAI = "spring-ai-openai";
    private static final String PROVIDER_OPENAI_COMPATIBLE = "spring-ai-openai-compatible";
    private static final String PROVIDER_DASHSCOPE = "spring-ai-alibaba-dashscope";

    @Bean(name = CHAT_MODEL_BEAN)
    public ChatModel agentFrameworkChatModel(AgentFrameworkProperties properties) {
        validateRequired(properties);
        String provider = normalizeProvider(properties.getProvider());
        return switch (provider) {
            case PROVIDER_OPENAI -> createOpenAiChatModel(properties, false);
            case PROVIDER_OPENAI_COMPATIBLE -> createOpenAiChatModel(properties, true);
            case PROVIDER_DASHSCOPE -> createDashScopeChatModel(properties);
            default -> throw new IllegalStateException(
                    "Unknown agent framework provider: " + properties.getProvider()
                            + ". Supported: " + PROVIDER_OPENAI + ", "
                            + PROVIDER_OPENAI_COMPATIBLE + ", "
                            + PROVIDER_DASHSCOPE
            );
        };
    }

    @Bean(name = CHAT_MEMORY_BEAN)
    public ChatMemory agentFrameworkChatMemory(
            ChatMemoryRepository chatMemoryRepository,
            AgentProperties agentProperties
    ) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(resolveMaxMessages(agentProperties))
                .build();
    }

    /** M4：对话记忆窗口条数，取配置值（>0），否则回落默认，保证「机会内不失忆」。 */
    static int resolveMaxMessages(AgentProperties agentProperties) {
        if (agentProperties == null || agentProperties.getConversationMemoryMaxMessages() == null
                || agentProperties.getConversationMemoryMaxMessages() <= 0) {
            return DEFAULT_CHAT_MEMORY_MAX_MESSAGES;
        }
        return agentProperties.getConversationMemoryMaxMessages();
    }

    @Bean(name = CHAT_CLIENT_BEAN)
    public ChatClient agentFrameworkChatClient(
            @Qualifier(CHAT_MODEL_BEAN) ChatModel agentFrameworkChatModel,
            @Qualifier(CHAT_MEMORY_BEAN) ChatMemory agentFrameworkChatMemory
    ) {
        // A1-3：默认挂对话记忆 advisor，会话 id 由调用方通过 advisor 参数传入
        return ChatClient.builder(agentFrameworkChatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(agentFrameworkChatMemory).build())
                .build();
    }

    private static ChatModel createOpenAiChatModel(AgentFrameworkProperties properties, boolean requireBaseUrl) {
        if (requireBaseUrl) {
            requireText(properties.getBaseUrl(), "careermate.agent.framework.base-url");
        }
        OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
                .apiKey(properties.getApiKey());
        if (StringUtils.hasText(properties.getBaseUrl())) {
            apiBuilder.baseUrl(properties.getBaseUrl().trim());
        }
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(properties.getModel().trim())
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(apiBuilder.build())
                .defaultOptions(options)
                .build();
    }

    private static ChatModel createDashScopeChatModel(AgentFrameworkProperties properties) {
        DashScopeApi.Builder apiBuilder = DashScopeApi.builder()
                .apiKey(properties.getApiKey());
        if (StringUtils.hasText(properties.getBaseUrl())) {
            apiBuilder.baseUrl(properties.getBaseUrl().trim());
        }
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel(properties.getModel().trim())
                .build();
        return DashScopeChatModel.builder()
                .dashScopeApi(apiBuilder.build())
                .defaultOptions(options)
                .build();
    }

    static void validateRequired(AgentFrameworkProperties properties) {
        requireText(properties.getApiKey(), "careermate.agent.framework.api-key");
        requireText(properties.getModel(), "careermate.agent.framework.model");
    }

    private static void requireText(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("Agent framework enabled but missing required property: " + propertyName);
        }
    }

    private static String normalizeProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return PROVIDER_OPENAI;
        }
        return provider.trim();
    }
}
