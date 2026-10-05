package com.careermate.keel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.agent.dto.AgentMessageRequest;
import com.careermate.agent.service.AgentStreamService;
import com.careermate.agent.session.AgentSessionService;
import com.careermate.agent.sse.AgentTaskRegistry;
import com.careermate.mapper.UserMapper;
import com.careermate.model.entity.UserEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** One console turn: open a CareerMate session and wait for the agent reply. */
@Service
public class KeelBridge {
    private final UserMapper users;
    private final AgentSessionService sessions;
    private final AgentStreamService streams;
    private final AgentTaskRegistry tasks;
    private final long configuredUserId;

    public KeelBridge(UserMapper users, AgentSessionService sessions, AgentStreamService streams,
                      AgentTaskRegistry tasks, @Value("${KEEL_CAREERMATE_USER_ID:0}") long configuredUserId) {
        this.users = users;
        this.sessions = sessions;
        this.streams = streams;
        this.tasks = tasks;
        this.configuredUserId = configuredUserId;
    }

    public String answer(String text) throws InterruptedException {
        var userId = userId();
        var sessionId = sessions.createSession(userId).getSessionId();
        var request = new AgentMessageRequest();
        request.setMessage(text);
        streams.stream(userId, sessionId, request);
        var deadline = System.nanoTime() + java.time.Duration.ofSeconds(50).toNanos();
        while (System.nanoTime() < deadline) {
            var reply = KeelReplies.lastAgent(sessions.getSession(userId, sessionId).getMessages());
            if (reply != null) {
                return reply;
            }
            if (!tasks.isRunning(sessionId)) {
                break;
            }
            Thread.sleep(200);
        }
        var reply = KeelReplies.lastAgent(sessions.getSession(userId, sessionId).getMessages());
        if (reply == null) {
            throw new IllegalStateException("CareerMate 没有返回回复");
        }
        return reply;
    }

    private Long userId() {
        if (configuredUserId > 0) {
            return configuredUserId;
        }
        var found = users.selectList(new LambdaQueryWrapper<UserEntity>().orderByAsc(UserEntity::getId).last("limit 1"));
        if (found.isEmpty()) {
            throw new IllegalStateException("CareerMate 还没有用户，底座无法代发对话");
        }
        return found.getFirst().getId();
    }
}
