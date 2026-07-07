package com.careermate.agent.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.mapper.AgentMessageMapper;
import com.careermate.mapper.AgentSessionMapper;
import com.careermate.model.entity.AgentMessageEntity;
import com.careermate.model.entity.AgentSessionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CareerMateChatMemoryRepositoryTest {

    private final AgentSessionMapper sessionMapper = mock(AgentSessionMapper.class);
    private final AgentMessageMapper messageMapper = mock(AgentMessageMapper.class);
    private final CareerMateChatMemoryRepository repository =
            new CareerMateChatMemoryRepository(sessionMapper, messageMapper);

    private AgentMessageEntity msg(String role, String content, int seq) {
        AgentMessageEntity m = new AgentMessageEntity();
        m.setRole(role);
        m.setContent(content);
        m.setSequenceNo(seq);
        m.setId((long) seq);
        return m;
    }

    @Test
    void findByConversationId_blank_returnsEmpty() {
        assertThat(repository.findByConversationId(" ")).isEmpty();
    }

    @Test
    void findByConversationId_unknownSession_returnsEmpty() {
        when(sessionMapper.selectOne(any())).thenReturn(null);
        assertThat(repository.findByConversationId("ext-1")).isEmpty();
    }

    @Test
    void findByConversationId_mapsRolesAndDropsTrailingUserTurn() {
        AgentSessionEntity session = new AgentSessionEntity();
        session.setId(10L);
        when(sessionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(session);
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                msg("user", "第一个问题", 1),
                msg("agent", "第一个回答", 2),
                msg("tool", "工具产物", 3),      // 非对话角色，跳过
                msg("user", "当前在飞的问题", 4)   // 末尾 user，丢弃
        ));

        List<Message> messages = repository.findByConversationId("ext-1");

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(0).getText()).isEqualTo("第一个问题");
        assertThat(messages.get(1)).isInstanceOf(AssistantMessage.class);
        assertThat(messages.get(1).getText()).isEqualTo("第一个回答");
    }

    @Test
    void saveAll_and_delete_areNoOps() {
        // 不抛异常即可（持久化由 AgentStreamService 负责）
        repository.saveAll("ext-1", List.of(new UserMessage("x")));
        repository.deleteByConversationId("ext-1");
        assertThat(repository.findConversationIds()).isEmpty();
    }
}
