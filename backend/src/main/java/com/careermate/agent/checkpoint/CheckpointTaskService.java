package com.careermate.agent.checkpoint;

import com.careermate.mapper.AgentRunMapper;
import com.careermate.model.entity.AgentRunEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * B3：可断点续跑的深度任务。每阶段落 checkpoint，在"选择深入方向"处 HITL 暂停，
 * 用户续跑注入决定后继续到完成；也可从某次运行分叉重试。逻辑为纯函数步进，可离线单测。
 */
@Slf4j
@Service
public class CheckpointTaskService {

    private final CheckpointedAgentEngine engine;
    private final CheckpointStore checkpointStore;
    private final AgentRunMapper agentRunMapper;
    private final CheckpointProperties properties;

    public CheckpointTaskService(CheckpointedAgentEngine engine, CheckpointStore checkpointStore,
                                 AgentRunMapper agentRunMapper, CheckpointProperties properties) {
        this.engine = engine;
        this.checkpointStore = checkpointStore;
        this.agentRunMapper = agentRunMapper;
        this.properties = properties;
    }

    /** 纯函数步进：created→analyzing→(HITL 暂停)awaiting_direction→resumed→finalizing→done。 */
    static final UnaryOperator<AgentState> STEP = s -> {
        Map<String, Object> d = new LinkedHashMap<>(s.data());
        String topic = String.valueOf(d.getOrDefault("topic", "求职深度分析"));
        String decision = String.valueOf(d.getOrDefault("userDecision", ""));
        switch (s.currentStep()) {
            case "created":
                d.put("analysis", "已针对「" + topic + "」完成初步分析，识别到可深入的方向。");
                return s.advance("analyzing", d);
            case "analyzing":
                return s.advance("awaiting_direction", d)
                        .pause("请选择要深入的方向（如：技能补齐 / 项目包装 / 面试准备）后继续");
            case "resumed":
                d.put("finalNote", "按你选择的方向「" + decision + "」继续深化。");
                return s.advance("finalizing", d);
            case "finalizing":
                d.put("result", "已生成基于方向「" + decision + "」的深化结论，可导出或分叉尝试其它方向。");
                return s.advance("done", d);
            default:
                return s.advance("done", d);
        }
    };

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /** 启动一次可续跑的深度任务，跑到 HITL 暂停点返回当前状态。 */
    public AgentState startRun(Long userId, String topic) {
        String runId = "run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String safeTopic = (topic == null || topic.isBlank()) ? "求职深度分析" : topic.trim();
        try {
            AgentRunEntity e = new AgentRunEntity();
            e.setRunId(runId);
            e.setUserId(userId);
            e.setStatus("RUNNING");
            e.setStartedAt(OffsetDateTime.now());
            agentRunMapper.insert(e);
        } catch (Exception ex) {
            log.warn("创建 agent_run 失败 runId={}: {}", runId, ex.getMessage());
        }
        // 预置初始状态，带上任务主题作为续跑上下文
        AgentState seed = new AgentState(runId, 0, "created", Map.of("topic", safeTopic), null, false);
        checkpointStore.save(runId, "created", seed, false, null);
        return engine.runOrResume(runId, userId, STEP);
    }

    /** 续跑：注入用户选择的方向，继续到完成。运行不存在或非本人返回 null。 */
    public AgentState resumeRun(Long userId, String runId, String decision) {
        if (!ownedBy(userId, runId)) {
            return null;
        }
        return engine.resume(runId, userId, decision, STEP);
    }

    /** 从某次运行分叉出新运行（用于换个方向重试）。返回新 runId；非本人或无快照返回 null。 */
    public String forkRun(Long userId, String runId) {
        if (!ownedBy(userId, runId)) {
            return null;
        }
        return engine.fork(runId, userId);
    }

    public List<AgentRunEntity> listRuns(Long userId) {
        return agentRunMapper.listRecentByUser(userId, Math.max(1, properties.getListLimit()));
    }

    /** 某次运行的最新状态（含 data/是否暂停）。非本人返回 empty。 */
    public Optional<AgentState> getRunState(Long userId, String runId) {
        if (!ownedBy(userId, runId)) {
            return Optional.empty();
        }
        return checkpointStore.loadLatest(runId);
    }

    private boolean ownedBy(Long userId, String runId) {
        if (userId == null || runId == null) {
            return false;
        }
        AgentRunEntity e = agentRunMapper.selectById(runId);
        return e != null && userId.equals(e.getUserId());
    }
}
