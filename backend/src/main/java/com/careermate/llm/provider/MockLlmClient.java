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
        String system = extractSystemContent(request == null ? null : request.getMessages());

        String toolReply = buildToolInvocationReply(system);
        if (toolReply != null) {
            return toolReply;
        }

        if (isResumeOnlyIntent(latest)) {
            String resumeTitle = extractResumeTitleFromSystem(system);
            if (resumeTitle != null && !resumeTitle.isBlank()) {
                return String.format(
                        "我已读取你的默认简历《%s》，建议你重点优化项目描述中的量化指标，并突出与目标岗位匹配的技能关键词。",
                        resumeTitle
                );
            }
            return "我还没有读取到默认简历，请先在简历页创建并设为默认。";
        }

        if (isJobMatchUserIntent(latest)) {
            JobMatchSnapshot snapshot = extractJobMatchSnapshotFromSystem(system);
            if (snapshot != null) {
                String missingPart = formatSkillPart(snapshot.missingSkills(), "暂无缺失技能");
                String matchedPart = formatSkillPart(snapshot.matchedSkills(), "暂无命中技能");
                return String.format(
                        "我已读取你最近的岗位匹配结果《%s》，当前匹配分为 %d 分。建议你优先补齐 %s，并在简历中强化 %s 的项目证据。",
                        snapshot.jobTitle(),
                        snapshot.matchScore(),
                        missingPart,
                        matchedPart
                );
            }
            return "我还没有读取到岗位匹配记录，请先在岗位匹配页录入 JD 并生成匹配结果。";
        }

        if (latest.contains("面试")) {
            return "这是 Mock 面试准备建议：先梳理项目亮点，再按八股题、系统设计、行为面三个维度准备高频问答。";
        }
        return "这是 Mock CareerMate 回复：我可以帮助你做简历优化、岗位匹配和面试准备。";
    }

    private String buildToolInvocationReply(String systemContent) {
        if (systemContent == null || !systemContent.contains("工具调用结果：")) {
            return null;
        }
        if (systemContent.contains("工具：get_default_resume")) {
            String title = extractToolDataField(systemContent, "title");
            if (title != null && !title.isBlank()) {
                return String.format(
                        "我已通过工具读取你的默认简历《%s》，建议你重点优化项目描述中的量化指标，并突出与目标岗位匹配的技能关键词。",
                        title
                );
            }
            return "我已通过工具读取你的默认简历，但当前还没有可用的默认简历，请先在简历页创建并设为默认。";
        }
        if (systemContent.contains("工具：get_latest_job_match")) {
            String jobTitle = extractToolDataField(systemContent, "jobTitle");
            String scoreText = extractToolDataField(systemContent, "matchScore");
            String missing = extractToolDataField(systemContent, "missingSkills");
            int score = 0;
            if (scoreText != null) {
                try {
                    score = Integer.parseInt(scoreText.trim());
                } catch (NumberFormatException ignored) {
                    score = 0;
                }
            }
            if (jobTitle != null && !jobTitle.isBlank()) {
                return String.format(
                        "我已通过工具读取你最近的岗位匹配《%s》，匹配分数为 %d 分，主要缺失技能包括 %s。",
                        jobTitle,
                        score,
                        missing == null || missing.isBlank() ? "暂无" : missing
                );
            }
            return "我已通过工具查询岗位匹配，但当前还没有匹配记录，请先录入 JD 生成匹配结果。";
        }
        if (systemContent.contains("工具：create_job_match")) {
            String jobTitle = extractToolDataField(systemContent, "jobTitle");
            String scoreText = extractToolDataField(systemContent, "matchScore");
            String matched = extractToolDataField(systemContent, "matchedSkills");
            String missing = extractToolDataField(systemContent, "missingSkills");
            return String.format(
                    "我已为你生成岗位匹配结果，匹配分数为 %s 分，岗位《%s》。主要命中 %s，缺失 %s。",
                    scoreText == null ? "0" : scoreText,
                    jobTitle == null ? "目标岗位" : jobTitle,
                    matched == null || matched.isBlank() ? "暂无" : matched,
                    missing == null || missing.isBlank() ? "暂无" : missing
            );
        }
        if (systemContent.contains("工具：create_interview_session")) {
            String title = extractToolDataField(systemContent, "title");
            String total = extractToolDataField(systemContent, "totalQuestions");
            return String.format(
                    "我已为你创建面试训练《%s》，可到面试特训页查看 %s 个问题。",
                    title == null ? "面试训练" : title,
                    total == null ? "5" : total
            );
        }
        if (systemContent.contains("工具：get_dashboard_overview")) {
            String resumeCount = extractToolDataField(systemContent, "resumeCount");
            String jobMatchCount = extractToolDataField(systemContent, "jobMatchCount");
            String interviewCount = extractToolDataField(systemContent, "interviewSessionCount");
            return String.format(
                    "我已读取你的求职看板：简历 %s 份，岗位匹配 %s 条，面试训练 %s 次。建议按看板提示继续完善简历、匹配和面试准备。",
                    resumeCount == null ? "0" : resumeCount,
                    jobMatchCount == null ? "0" : jobMatchCount,
                    interviewCount == null ? "0" : interviewCount
            );
        }
        return null;
    }

    private String extractToolDataField(String systemContent, String fieldName) {
        String marker = "- " + fieldName + "：";
        int idx = systemContent.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        int start = idx + marker.length();
        int end = systemContent.indexOf('\n', start);
        if (end < 0) {
            end = systemContent.length();
        }
        return systemContent.substring(start, end).trim();
    }

    private boolean isResumeOnlyIntent(String latest) {
        return latest.contains("简历") && !isJobMatchUserIntent(latest);
    }

    private boolean isJobMatchUserIntent(String latest) {
        if (latest.contains("岗位") || latest.contains("匹配") || latest.contains("差距")
                || latest.contains("缺失") || latest.contains("面试")) {
            return true;
        }
        return latest.contains("优化")
                && (latest.contains("岗位") || latest.contains("刚才") || latest.contains("匹配"));
    }

    private String formatSkillPart(List<String> skills, String fallback) {
        if (skills == null || skills.isEmpty()) {
            return fallback;
        }
        if (skills.size() == 1) {
            return skills.get(0);
        }
        return skills.get(0) + "、" + skills.get(1);
    }

    private JobMatchSnapshot extractJobMatchSnapshotFromSystem(String systemContent) {
        if (systemContent == null || !systemContent.contains("最近岗位匹配结果：")) {
            return null;
        }
        String jobTitle = extractFieldValue(systemContent, "岗位：");
        if (jobTitle == null || jobTitle.isBlank()) {
            return null;
        }
        int matchScore = 0;
        String scoreText = extractFieldValue(systemContent, "匹配分数：");
        if (scoreText != null && !scoreText.isBlank()) {
            try {
                matchScore = Integer.parseInt(scoreText.trim());
            } catch (NumberFormatException ignored) {
                matchScore = 0;
            }
        }
        List<String> matched = extractListField(systemContent, "命中技能：");
        List<String> missing = extractListField(systemContent, "缺失技能：");
        return new JobMatchSnapshot(jobTitle, matchScore, matched, missing);
    }

    private String extractFieldValue(String systemContent, String marker) {
        int idx = systemContent.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        int start = idx + marker.length();
        int end = systemContent.indexOf('\n', start);
        if (end < 0) {
            end = systemContent.length();
        }
        return systemContent.substring(start, end).trim();
    }

    private List<String> extractListField(String systemContent, String marker) {
        String value = extractFieldValue(systemContent, marker);
        if (value == null || value.isBlank() || "无".equals(value)) {
            return List.of();
        }
        String[] parts = value.split("、");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private record JobMatchSnapshot(String jobTitle, int matchScore, List<String> matchedSkills, List<String> missingSkills) {
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
