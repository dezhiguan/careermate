package com.careermate.llm.provider;

import com.careermate.llm.LlmClient;
import com.careermate.llm.LlmProperties;
import com.careermate.llm.StreamCallback;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.llm.dto.ToolCallRequest;
import com.careermate.llm.dto.ToolCallResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MockLlmClient implements LlmClient {

    private static final String PROVIDER = "mock";
    private final LlmProperties llmProperties;

    public MockLlmClient(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        long start = System.currentTimeMillis();
        String content = buildMockContent(request);
        long latency = System.currentTimeMillis() - start;

        int inputTokens = estimateTokens(extractAllContents(request));
        int outputTokens = estimateTokens(content);

        return ChatResponse.builder()
                .content(content)
                .model(resolveModel(request))
                .provider(PROVIDER)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .latencyMs(latency)
                .finishReason("stop")
                .build();
    }

    @Override
    public void streamChat(ChatRequest request, StreamCallback callback) {
        try {
            ChatResponse base = chat(request);
            String content = base.getContent() == null ? "" : base.getContent();
            List<String> chunks = splitBySize(content, 8);
            for (String chunk : chunks) {
                callback.onToken(chunk);
            }
            callback.onComplete(base);
        } catch (Throwable t) {
            callback.onError(t);
        }
    }

    @Override
    public ToolCallResponse toolCall(ToolCallRequest request) {
        long start = System.currentTimeMillis();
        String userContent = extractLatestUserContent(request == null ? null : request.getMessages());
        String content = "当前为 Mock ToolCall 预留接口，尚未执行真实工具调用。输入摘要：" + userContent;
        return ToolCallResponse.builder()
                .content(content)
                .toolCalls(Collections.emptyList())
                .model(request != null && request.getModel() != null ? request.getModel() : llmProperties.getModel())
                .provider(PROVIDER)
                .latencyMs(System.currentTimeMillis() - start)
                .build();
    }

    private String resolveModel(ChatRequest request) {
        if (request != null && request.getModel() != null && !request.getModel().isBlank()) {
            return request.getModel();
        }
        return llmProperties.getModel();
    }

    private String buildMockContent(ChatRequest request) {
        String latest = extractLatestUserContent(request == null ? null : request.getMessages()).toLowerCase(Locale.ROOT);
        if (latest.contains("简历")) {
            String system = extractSystemContent(request == null ? null : request.getMessages());
            String resumeTitle = extractResumeTitleFromSystem(system);
            if (resumeTitle != null && !resumeTitle.isBlank()) {
                return String.format(
                        "我已读取你的默认简历《%s》，建议你重点优化项目描述中的量化指标，并突出与目标岗位匹配的技能关键词。",
                        resumeTitle
                );
            }
            return "我还没有读取到默认简历，请先在简历页创建并设为默认。";
        }
        if (latest.contains("岗位") || latest.contains("匹配")) {
            return "这是 Mock 岗位匹配结果：你当前更匹配后端工程师与数据平台工程师方向，建议优先投递技术栈重合度高的岗位。";
        }
        if (latest.contains("面试")) {
            return "这是 Mock 面试准备建议：先梳理项目亮点，再按八股题、系统设计、行为面三个维度准备高频问答。";
        }
        return "这是 Mock CareerMate 回复：我可以帮助你做简历优化、岗位匹配和面试准备。";
    }

    private String extractSystemContent(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (ChatMessage msg : messages) {
            if (msg != null && "system".equalsIgnoreCase(msg.getRole())) {
                return msg.getContent() == null ? "" : msg.getContent();
            }
        }
        return "";
    }

    private String extractResumeTitleFromSystem(String systemContent) {
        if (systemContent == null || systemContent.isBlank()) {
            return null;
        }
        if (!systemContent.contains("用户默认简历：")) {
            return null;
        }
        String marker = "标题：";
        int idx = systemContent.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        int start = idx + marker.length();
        int end = systemContent.indexOf('\n', start);
        if (end < 0) {
            end = systemContent.length();
        }
        String title = systemContent.substring(start, end).trim();
        return title.isEmpty() ? null : title;
    }

    private String extractLatestUserContent(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (msg != null && "user".equalsIgnoreCase(msg.getRole())) {
                return msg.getContent() == null ? "" : msg.getContent();
            }
        }
        ChatMessage last = messages.get(messages.size() - 1);
        return last == null || last.getContent() == null ? "" : last.getContent();
    }

    private String extractAllContents(ChatRequest request) {
        if (request == null || request.getMessages() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatMessage message : request.getMessages()) {
            if (message != null && message.getContent() != null) {
                sb.append(message.getContent()).append('\n');
            }
        }
        return sb.toString();
    }

    private int estimateTokens(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, content.length() / 2);
    }

    private List<String> splitBySize(String content, int size) {
        if (content == null || content.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < content.length(); i += size) {
            int end = Math.min(content.length(), i + size);
            result.add(content.substring(i, end));
        }
        return result;
    }
}
