package com.careermate.agent.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.mapper.AgentMessageMapper;
import com.careermate.mapper.AgentSessionMapper;
import com.careermate.model.entity.AgentMessageEntity;
import com.careermate.model.entity.AgentSessionEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * A1-3：Spring AI {@link ChatMemoryRepository} 实现，复用既有 {@code agent_messages} 表，不新建表。
 *
 * <p>只读取历史：conversationId 即外部 sessionId，映射到 {@code agent_sessions.id} 后按序读 {@code agent_messages}。
 * 写入为 no-op —— 消息持久化仍由 {@code AgentStreamService} 单一负责（appendMessage），避免与 advisor 双写。
 * 读取时丢弃末尾的 user 消息（即当前在飞的这一轮，已被 appendMessage 落库），防止与 ChatClient 的
 * {@code .user()} 重复注入。
 */
@Slf4j
@Component
public class CareerMateChatMemoryRepository implements ChatMemoryRepository {

    private final AgentSessionMapper sessionMapper;
    private final AgentMessageMapper messageMapper;

    public CareerMateChatMemoryRepository(AgentSessionMapper sessionMapper, AgentMessageMapper messageMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
    }

    @Override
    public List<String> findConversationIds() {
        return List.of();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return List.of();
        }
        AgentSessionEntity session = sessionMapper.selectOne(
                new LambdaQueryWrapper<AgentSessionEntity>()
                        .eq(AgentSessionEntity::getSessionId, conversationId)
                        .last("limit 1"));
        if (session == null) {
            return List.of();
        }
        List<AgentMessageEntity> rows = messageMapper.selectList(
                new LambdaQueryWrapper<AgentMessageEntity>()
                        .eq(AgentMessageEntity::getSessionId, session.getId())
                        .orderByAsc(AgentMessageEntity::getSequenceNo)
                        .orderByAsc(AgentMessageEntity::getId));

        List<Message> messages = new ArrayList<>();
        for (AgentMessageEntity row : rows) {
            Message message = toMessage(row);
            if (message != null) {
                messages.add(message);
            }
        }
        // 丢弃末尾 user 消息（当前在飞轮次），避免与 ChatClient .user() 重复
        if (!messages.isEmpty() && messages.get(messages.size() - 1) instanceof UserMessage) {
            messages.remove(messages.size() - 1);
        }
        return messages;
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        // no-op：持久化由 AgentStreamService 负责，保持单一写入源
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        // no-op：会话清理由会话生命周期管理负责
    }

    private Message toMessage(AgentMessageEntity row) {
        if (row == null || !StringUtils.hasText(row.getContent())) {
            return null;
        }
        String role = row.getRole() == null ? "" : row.getRole().toLowerCase();
        return switch (role) {
            case "user" -> new UserMessage(row.getContent());
            case "agent", "assistant" -> new AssistantMessage(row.getContent());
            case "system" -> new SystemMessage(row.getContent());
            default -> null; // tool 等其它角色不进对话记忆
        };
    }
}
