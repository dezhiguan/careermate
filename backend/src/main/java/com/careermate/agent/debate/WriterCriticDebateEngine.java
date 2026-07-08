package com.careermate.agent.debate;

import com.careermate.mapper.AgentCollabMessageMapper;
import com.careermate.mapper.AgentCollabSessionMapper;
import com.careermate.model.entity.AgentCollabMessageEntity;
import com.careermate.model.entity.AgentCollabSessionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * B1：Writer-Critic 进程内 debate 收敛。writer 出稿 → critic 评审 → 不满意则 writer 改 → 直到共识或硬上限。
 * 无消息总线/无 A2A 递归；硬上限 ≤5 轮；critic 分数仅内部用；全程落 agent_collab_* 便于回放。
 */
@Slf4j
@Service
public class WriterCriticDebateEngine {

    private static final int HARD_MAX_ROUNDS = 5;

    private final ResumeWriter writer;
    private final ResumeCritic critic;
    private final DebateProperties properties;
    private final AgentCollabSessionMapper sessionMapper;
    private final AgentCollabMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    public WriterCriticDebateEngine(ResumeWriter writer, ResumeCritic critic, DebateProperties properties,
                                    AgentCollabSessionMapper sessionMapper, AgentCollabMessageMapper messageMapper,
                                    ObjectMapper objectMapper) {
        this.writer = writer;
        this.critic = critic;
        this.properties = properties;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public DebateResult debate(String jd, String resume) {
        int maxRounds = Math.min(HARD_MAX_ROUNDS, Math.max(1, properties.getMaxRounds()));
        String sessionId = "collab_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        createSession(sessionId, maxRounds);

        String draft = writer.write(jd, resume);
        persistMessage(sessionId, "writer", "critic", "DRAFT", 0, Map.of("draftLength", draft.length()));

        String status = "MAX_ROUND";
        String lastSuggestion = "";
        int executed = 0;
        for (int round = 1; round <= maxRounds; round++) {
            executed = round;
            CriticVerdict verdict = critic.review(jd, draft);
            persistMessage(sessionId, "critic", "writer", "REVIEW", round, Map.of(
                    "satisfaction", verdict.satisfaction(),
                    "criticism", verdict.criticism(),
                    "suggestion", verdict.suggestion()));
            lastSuggestion = verdict.suggestion();

            if (verdict.satisfaction() >= properties.getConsensusThreshold()) {
                status = "CONSENSUS";
                break;
            }
            if (round == maxRounds) {
                break;
            }
            // writer 仅收到 criticism/suggestion，不含 satisfaction 分数
            draft = writer.revise(jd, resume, draft, verdict.criticism(), verdict.suggestion());
            persistMessage(sessionId, "writer", "critic", "REVISION", round, Map.of("draftLength", draft.length()));
        }

        finishSession(sessionId, status);
        return new DebateResult(sessionId, draft, executed, status, lastSuggestion);
    }

    private void createSession(String sessionId, int maxRounds) {
        try {
            AgentCollabSessionEntity e = new AgentCollabSessionEntity();
            e.setId(sessionId);
            e.setType("DEBATE");
            e.setParticipants(writeJson(java.util.List.of("writer", "critic")));
            e.setConfig(writeJson(Map.of("maxRounds", maxRounds, "consensusThreshold", properties.getConsensusThreshold())));
            e.setStatus("RUNNING");
            e.setStartedAt(OffsetDateTime.now());
            sessionMapper.insert(e);
        } catch (Exception ex) {
            log.warn("debate 会话落库失败（不影响收敛）: {}", ex.getMessage());
        }
    }

    private void finishSession(String sessionId, String status) {
        try {
            AgentCollabSessionEntity e = new AgentCollabSessionEntity();
            e.setId(sessionId);
            e.setStatus(status);
            e.setFinishedAt(OffsetDateTime.now());
            sessionMapper.updateById(e);
        } catch (Exception ex) {
            log.warn("debate 会话收尾落库失败: {}", ex.getMessage());
        }
    }

    private void persistMessage(String sessionId, String from, String to, String type, int round, Map<String, Object> payload) {
        try {
            AgentCollabMessageEntity e = new AgentCollabMessageEntity();
            e.setSessionId(sessionId);
            e.setFromAgent(from);
            e.setToAgent(to);
            e.setMsgType(type);
            e.setRoundNo(round);
            e.setPayload(writeJson(payload));
            messageMapper.insert(e);
        } catch (Exception ex) {
            log.warn("debate 消息落库失败: {}", ex.getMessage());
        }
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }
}
