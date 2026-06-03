package com.careermate.llm;

import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.provider.MockLlmClient;
import com.careermate.llm.LlmProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MockLlmResumeContextTest {

    private final MockLlmClient client = new MockLlmClient(new LlmProperties());

    @Test
    void mockReplyMentionsDefaultResumeTitleWhenPresent() {
        String system = """
                你是 CareerMate 求职 Agent。

                用户默认简历：
                标题：官德志-后端简历
                正文：
                三年 Java 经验
                """;
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.builder().role("system").content(system).build(),
                        ChatMessage.builder().role("user").content("帮我分析简历").build()
                ))
                .build();

        String content = client.chat(request).getContent();
        assertTrue(content.contains("官德志-后端简历"), content);
        assertTrue(content.contains("我已读取你的默认简历"), content);
    }

    @Test
    void mockReplyPromptsCreateDefaultWhenMissing() {
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.builder().role("system").content("当前用户暂无默认简历。").build(),
                        ChatMessage.builder().role("user").content("帮我分析简历").build()
                ))
                .build();

        String content = client.chat(request).getContent();
        assertTrue(content.contains("还没有读取到默认简历"), content);
    }
}
