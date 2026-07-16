package com.careermate.agent.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * M4 不失忆：验证对话记忆窗口条数从配置解析，缺失/非法时回落默认 100（而非旧的 20）。
 */
class AgentFrameworkChatMemoryConfigTest {

    @Test
    void defaultPropertyValueIsRaised() {
        assertEquals(100, new AgentProperties().getConversationMemoryMaxMessages());
    }

    @Test
    void resolveUsesConfiguredValue() {
        AgentProperties p = new AgentProperties();
        p.setConversationMemoryMaxMessages(250);
        assertEquals(250, AgentFrameworkConfig.resolveMaxMessages(p));
    }

    @Test
    void resolveFallsBackWhenNullPropertiesOrValue() {
        assertEquals(100, AgentFrameworkConfig.resolveMaxMessages(null));

        AgentProperties nullValue = new AgentProperties();
        nullValue.setConversationMemoryMaxMessages(null);
        assertEquals(100, AgentFrameworkConfig.resolveMaxMessages(nullValue));
    }

    @Test
    void resolveFallsBackWhenNonPositive() {
        AgentProperties zero = new AgentProperties();
        zero.setConversationMemoryMaxMessages(0);
        assertEquals(100, AgentFrameworkConfig.resolveMaxMessages(zero));

        AgentProperties negative = new AgentProperties();
        negative.setConversationMemoryMaxMessages(-5);
        assertEquals(100, AgentFrameworkConfig.resolveMaxMessages(negative));
    }
}
