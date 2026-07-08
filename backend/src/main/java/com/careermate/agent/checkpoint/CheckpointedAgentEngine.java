package com.careermate.agent.checkpoint;

import com.careermate.mapper.AgentRunMapper;
import com.careermate.model.entity.AgentRunEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * B3：带 checkpoint 的执行引擎。每个节点边界后 save 快照；崩溃后 runOrResume 从最近 checkpoint 继续；
 * 遇 HITL 暂停立即持久化并返回（awaiting_user），外部 resume 注入用户决定后继续。裁剪：不做 fork/时间旅行。
 */
@Slf4j
@Service
public class CheckpointedAgentEngine {

    private final CheckpointStore checkpointStore;
    private final AgentRunMapper agentRunMapper;

    public CheckpointedAgentEngine(CheckpointStore checkpointStore, AgentRunMapper agentRunMapper) {
        this.checkpointStore = checkpointStore;
        this.agentRunMapper = agentRunMapper;
    }

    /**
     * 运行或从最近 checkpoint 续跑。step 每次推进一个节点；返回 paused 或 done 的终态 state。
     *
     * @param step 给定当前 state 产出下一 state（应在 pause 节点返回 state.pause(action)）
     */
    public AgentState runOrResume(String runId, Long userId, UnaryOperator<AgentState> step) {
        AgentState state = checkpointStore.loadLatest(runId)
                .orElseGet(() -> {
                    ensureRun(runId, userId);
                    AgentState init = AgentState.initial(runId);
                    checkpointStore.save(runId, init.currentStep(), init, false, null);
                    return init;
                });

        int guard = 0;
        while (!state.isTerminal() && guard++ < 100) {
            AgentState next = step.apply(state);
            checkpointStore.save(runId, next.currentStep(), next, next.paused(), next.pendingAction());
            state = next;
            if (state.paused()) {
                updateRunStatus(runId, "PAUSED");
                return state; // 立即返回，等外部 resume
            }
        }
        updateRunStatus(runId, "DONE");
        return state;
    }

    /** HITL：注入用户决定后从暂停点继续。 */
    public AgentState resume(String runId, Long userId, String userDecision, UnaryOperator<AgentState> step) {
        Optional<AgentState> paused = checkpointStore.loadLatest(runId);
        if (paused.isEmpty()) {
            return null;
        }
        AgentState resumed = new AgentState(runId, paused.get().stepIndex(), "resumed",
                Map.of("userDecision", userDecision == null ? "" : userDecision), null, false);
        checkpointStore.save(runId, "resumed", resumed, false, null);
        updateRunStatus(runId, "RUNNING");
        return runOrResume(runId, userId, step);
    }

    private void ensureRun(String runId, Long userId) {
        try {
            if (agentRunMapper.selectById(runId) == null) {
                AgentRunEntity e = new AgentRunEntity();
                e.setRunId(runId);
                e.setUserId(userId);
                e.setStatus("RUNNING");
                e.setStartedAt(OffsetDateTime.now());
                agentRunMapper.insert(e);
            }
        } catch (Exception ex) {
            log.warn("创建 agent_run 失败 runId={}: {}", runId, ex.getMessage());
        }
    }

    private void updateRunStatus(String runId, String status) {
        try {
            AgentRunEntity e = new AgentRunEntity();
            e.setRunId(runId);
            e.setStatus(status);
            if (!"RUNNING".equals(status)) {
                e.setFinishedAt(OffsetDateTime.now());
            }
            agentRunMapper.updateById(e);
        } catch (Exception ex) {
            log.warn("更新 agent_run 状态失败 runId={}: {}", runId, ex.getMessage());
        }
    }
}
