package com.careermate.agent.context;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.agent.config.AgentProperties;
import com.careermate.common.exception.BizException;
import com.careermate.mapper.AgentMessageMapper;
import com.careermate.mapper.AgentSessionMapper;
import com.careermate.model.entity.AgentMessageEntity;
import com.careermate.model.entity.AgentSessionEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class AgentConversationContextProvider {

    private static final String HISTORY_HEADER = "【当前会话历史】";

    private final AgentSessionMapper agentSessionMapper;
    private final AgentMessageMapper agentMessageMapper;
    private final AgentProperties agentProperties;

    public AgentConversationContextProvider(
            AgentSessionMapper agentSessionMapper,
            AgentMessageMapper agentMessageMapper,
            AgentProperties agentProperties
    ) {
        this.agentSessionMapper = agentSessionMapper;
        this.agentMessageMapper = agentMessageMapper;
        this.agentProperties = agentProperties;
    }

    public ConversationContextResult load(Long userId, String sessionId, String excludeUserMessage) {
        if (userId == null || sessionId == null || sessionId.isBlank()) {
            return ConversationContextResult.empty();
        }
        try {
            AgentSessionEntity session = agentSessionMapper.selectOne(
                    new LambdaQueryWrapper<AgentSessionEntity>()
                            .eq(AgentSessionEntity::getSessionId, sessionId)
                            .eq(AgentSessionEntity::getUserId, userId)
                            .last("LIMIT 1")
            );
            if (session == null) {
                return ConversationContextResult.empty();
            }

            int maxMessages = Math.max(1, agentProperties.getConversationContextMaxMessages());
            List<AgentMessageEntity> recent = agentMessageMapper.selectList(
                    new LambdaQueryWrapper<AgentMessageEntity>()
                            .eq(AgentMessageEntity::getSessionId, session.getId())
                            .eq(AgentMessageEntity::getUserId, userId)
                            .orderByDesc(AgentMessageEntity::getSequenceNo)
                            .last("LIMIT " + (maxMessages + 1))
            );
            if (recent == null || recent.isEmpty()) {
                return ConversationContextResult.empty();
            }

            List<AgentMessageEntity> filtered = excludeCurrentUserMessage(recent, excludeUserMessage);
            if (filtered.isEmpty()) {
                return ConversationContextResult.empty();
            }

            List<AgentMessageEntity> selected = filtered.size() > maxMessages
                    ? new ArrayList<>(filtered.subList(0, maxMessages))
                    : new ArrayList<>(filtered);
            Collections.reverse(selected);

            String formatted = formatMessages(selected);
            int maxChars = Math.max(1, agentProperties.getConversationContextMaxChars());
            formatted = applyCharBudget(formatted, maxChars);
            if (formatted == null || formatted.isBlank()) {
                return ConversationContextResult.empty();
            }

            int messageCount = countHistoryLines(formatted);
            return ConversationContextResult.builder()
                    .available(true)
                    .contextText(formatted.trim())
                    .messageCount(messageCount)
                    .charCount(formatted.length())
                    .loadFailed(false)
                    .build();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to load conversation context: userId={}, sessionId={}", userId, sessionId, e);
            return ConversationContextResult.failed();
        }
    }

    private List<AgentMessageEntity> excludeCurrentUserMessage(
            List<AgentMessageEntity> recentDesc,
            String excludeUserMessage
    ) {
        List<AgentMessageEntity> copy = new ArrayList<>(recentDesc);
        if (copy.isEmpty()) {
            return copy;
        }
        AgentMessageEntity latest = copy.get(0);
        if (!"user".equalsIgnoreCase(latest.getRole())) {
            return copy;
        }
        String exclude = excludeUserMessage == null ? "" : excludeUserMessage.trim();
        String latestContent = latest.getContent() == null ? "" : latest.getContent().trim();
        if (!exclude.isEmpty() && exclude.equals(latestContent)) {
            copy.remove(0);
        }
        return copy;
    }

    private String formatMessages(List<AgentMessageEntity> chronological) {
        StringBuilder sb = new StringBuilder(HISTORY_HEADER);
        for (AgentMessageEntity message : chronological) {
            if (message == null || message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }
            sb.append('\n').append(toHistoryRole(message.getRole())).append(": ")
                    .append(message.getContent().trim());
        }
        if (sb.length() == HISTORY_HEADER.length()) {
            return "";
        }
        return sb.toString();
    }

    private String toHistoryRole(String role) {
        if ("user".equalsIgnoreCase(role)) {
            return "user";
        }
        return "assistant";
    }

    private String applyCharBudget(String formatted, int maxChars) {
        if (formatted.length() <= maxChars) {
            return formatted;
        }
        String[] lines = formatted.split("\n", -1);
        if (lines.length <= 1) {
            return truncateTail(formatted, maxChars);
        }

        List<String> bodyLines = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            bodyLines.add(lines[i]);
        }

        while (!bodyLines.isEmpty()) {
            String candidate = rebuildHistory(bodyLines);
            if (candidate.length() <= maxChars) {
                return candidate;
            }
            bodyLines.remove(0);
        }
        return "";
    }

    private String rebuildHistory(List<String> bodyLines) {
        StringBuilder sb = new StringBuilder(HISTORY_HEADER);
        for (String line : bodyLines) {
            sb.append('\n').append(line);
        }
        return sb.toString();
    }

    private String truncateTail(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }

    private int countHistoryLines(String formatted) {
        int count = 0;
        for (String line : formatted.split("\n")) {
            if (line.startsWith("user:") || line.startsWith("assistant:")) {
                count++;
            }
        }
        return count;
    }
}
