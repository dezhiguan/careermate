package com.careermate.agent.config;

import com.careermate.CareerMateApplication;
import com.careermate.llm.LlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentFrameworkConfigTest {

    @SpringBootTest(classes = CareerMateApplication.class)
    @ActiveProfiles("test")
    public static class WhenDisabledByDefault {

        @Autowired
        private ApplicationContext applicationContext;

        @Autowired
        private LlmClient llmClient;

        @Test
        void shouldKeepExistingLlmClientWithoutAgentFrameworkBeans() {
            assertNotNull(llmClient);
            assertTrue(applicationContext.getBeansOfType(ChatClient.class).isEmpty());
            assertTrue(applicationContext.getBeansOfType(ChatModel.class).isEmpty());
        }
    }

    @SpringBootTest(classes = CareerMateApplication.class)
    @ActiveProfiles("test")
    @TestPropertySource(properties = {
            "careermate.agent.framework.enabled=true",
            "careermate.agent.framework.provider=spring-ai-openai-compatible",
            "careermate.agent.framework.model=test-model",
            "careermate.agent.framework.base-url=https://example.com/v1",
            "careermate.agent.framework.api-key=test-api-key"
    })
    public static class WhenOpenAiCompatibleEnabled {

        @Autowired
        private LlmClient llmClient;

        @Autowired
        @Qualifier(AgentFrameworkConfig.CHAT_CLIENT_BEAN)
        private ChatClient agentFrameworkChatClient;

        @Autowired
        @Qualifier(AgentFrameworkConfig.CHAT_MODEL_BEAN)
        private ChatModel agentFrameworkChatModel;

        @Test
        void shouldInitializeBypassBeansWithoutReplacingLlmClient() {
            assertNotNull(llmClient);
            assertNotNull(agentFrameworkChatClient);
            assertNotNull(agentFrameworkChatModel);
            assertInstanceOf(OpenAiChatModel.class, agentFrameworkChatModel);
        }
    }

    @SpringBootTest(classes = CareerMateApplication.class)
    @ActiveProfiles("test")
    @TestPropertySource(properties = {
            "careermate.agent.framework.enabled=true",
            "careermate.agent.framework.provider=spring-ai-alibaba-dashscope",
            "careermate.agent.framework.model=qwen-plus",
            "careermate.agent.framework.api-key=test-dashscope-key"
    })
    public static class WhenDashScopeEnabled {

        @Autowired
        @Qualifier(AgentFrameworkConfig.CHAT_CLIENT_BEAN)
        private ChatClient agentFrameworkChatClient;

        @Autowired
        @Qualifier(AgentFrameworkConfig.CHAT_MODEL_BEAN)
        private ChatModel agentFrameworkChatModel;

        @Test
        void shouldInitializeDashScopeBypassBeans() {
            assertNotNull(agentFrameworkChatClient);
            assertInstanceOf(com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel.class, agentFrameworkChatModel);
        }
    }

    @Test
    void shouldFailWhenRequiredPropertiesMissing() {
        AgentFrameworkProperties properties = new AgentFrameworkProperties();
        properties.setEnabled(true);
        properties.setProvider("spring-ai-openai-compatible");
        properties.setModel("test-model");
        properties.setBaseUrl("https://example.com/v1");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> AgentFrameworkConfig.validateRequired(properties)
        );
        assertTrue(exception.getMessage().contains("careermate.agent.framework.api-key"));
    }

    @Test
    void shouldFailWhenOpenAiCompatibleMissingBaseUrl() {
        AgentFrameworkProperties properties = enabledProperties("spring-ai-openai-compatible");
        properties.setBaseUrl("");

        AgentFrameworkConfig config = new AgentFrameworkConfig();
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> config.agentFrameworkChatModel(properties)
        );
        assertTrue(exception.getMessage().contains("careermate.agent.framework.base-url"));
    }

    @Test
    void shouldFailWhenProviderUnknown() {
        AgentFrameworkProperties properties = enabledProperties("unsupported-provider");

        AgentFrameworkConfig config = new AgentFrameworkConfig();
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> config.agentFrameworkChatModel(properties)
        );
        assertTrue(exception.getMessage().contains("Unknown agent framework provider"));
    }

    @Test
    void shouldCreateOpenAiChatModelWhenProviderIsOpenAi() {
        AgentFrameworkProperties properties = enabledProperties("spring-ai-openai");
        properties.setModel("gpt-4o-mini");
        properties.setBaseUrl("");

        AgentFrameworkConfig config = new AgentFrameworkConfig();
        ChatModel chatModel = assertDoesNotThrow(() -> config.agentFrameworkChatModel(properties));
        assertInstanceOf(OpenAiChatModel.class, chatModel);
    }

    private static AgentFrameworkProperties enabledProperties(String provider) {
        AgentFrameworkProperties properties = new AgentFrameworkProperties();
        properties.setEnabled(true);
        properties.setProvider(provider);
        properties.setModel("test-model");
        properties.setBaseUrl("https://example.com/v1");
        properties.setApiKey("test-api-key");
        return properties;
    }
}
