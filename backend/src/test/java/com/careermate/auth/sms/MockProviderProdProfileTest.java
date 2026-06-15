package com.careermate.auth.sms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "DB_URL=jdbc:postgresql://localhost:5432/careermate_test_db",
        "DB_USERNAME=careermate",
        "DB_PASSWORD=careermate",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                + "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration,"
                + "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration,"
                + "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration,"
                + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration,"
                + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration,"
                + "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration,"
                + "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeChatAutoConfiguration,"
                + "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAgentAutoConfiguration,"
                + "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeImageAutoConfiguration,"
                + "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAudioSpeechAutoConfiguration,"
                + "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAudioTranscriptionAutoConfiguration,"
                + "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeRerankAutoConfiguration,"
                + "com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeEmbeddingAutoConfiguration"
})
class MockProviderProdProfileTest {

    @MockBean
    private MobileSmsAuthProvider mobileSmsAuthProvider;

    @Autowired(required = false)
    private MockPnvsSmsAuthProvider mockPnvsSmsAuthProvider;

    @Test
    void mockProviderNotLoadedInProdProfile() {
        assertThat(mockPnvsSmsAuthProvider).isNull();
    }
}
