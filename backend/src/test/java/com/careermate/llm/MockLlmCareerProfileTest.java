package com.careermate.llm;

import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.provider.MockLlmClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MockLlmCareerProfileTest {

    private final MockLlmClient client = new MockLlmClient(new LlmProperties());

    @Test
    void mockRecallsJavaBackendGoalFromCareerProfile() {
        String system = """
                你是 CareerMate 求职 Agent。

                【用户求职画像】
                目标岗位：Java 后端开发工程师
                目标城市：杭州
                """;
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.builder().role("system").content(system).build(),
                        ChatMessage.builder().role("user").content("你还记得我的目标吗").build()
                ))
                .build();

        String content = client.chat(request).getContent();
        assertTrue(content.contains("Java 后端"), content);
        assertTrue(content.contains("记得"), content);
    }

    @Test
    void mockSuggestsPreparationBasedOnCareerProfile() {
        String system = """
                你是 CareerMate 求职 Agent。

                【用户求职画像】
                目标岗位：Java 后端开发工程师
                """;
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.builder().role("system").content(system).build(),
                        ChatMessage.builder().role("user").content("我现在应该优先准备什么").build()
                ))
                .build();

        String content = client.chat(request).getContent();
        assertTrue(content.contains("Java"), content);
        assertTrue(content.contains("准备"), content);
    }
}
