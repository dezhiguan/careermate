package com.careermate.keel;

import com.careermate.agent.dto.AgentMessageResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeelRepliesTest {
    @Test void keepsTheLatestAgentReply() {
        var first = AgentMessageResponse.builder().role("user").content("改简历").build();
        var draft = AgentMessageResponse.builder().role("agent").content("第一稿").build();
        var latest = AgentMessageResponse.builder().role("agent").content("第二稿").build();
        assertThat(KeelReplies.lastAgent(List.of(first, draft, latest))).isEqualTo("第二稿");
        assertThat(KeelReplies.lastAgent(List.of(first))).isNull();
        assertThat(KeelReplies.lastAgent(null)).isNull();
    }

    @Test void finalEventEscapesTheAnswer() {
        var body = KeelReplies.finalEvent("第一行\n\"引号\"", "tr", "run");
        assertThat(body).startsWith("event: final\n");
        assertThat(body).contains("\\n").contains("\\\"");
        assertThat(body).contains("\"trace_id\":\"tr\"").contains("\"run_id\":\"run\"");
    }
}
