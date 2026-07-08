package com.careermate.agent.checkpoint;

import com.careermate.mapper.AgentCheckpointMapper;
import com.careermate.model.entity.AgentCheckpointEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * B3：checkpoint 存取。节点边界 save（相邻 state_hash 相同则跳过写入，去重）；loadLatest 崩溃续跑。
 */
@Slf4j
@Service
public class CheckpointStore {

    private final AgentCheckpointMapper mapper;
    private final StateCodec codec;

    public CheckpointStore(AgentCheckpointMapper mapper, StateCodec codec) {
        this.mapper = mapper;
        this.codec = codec;
    }

    /** @return 是否写入了新 checkpoint（去重跳过返回 false）。 */
    public boolean save(String runId, String stepName, AgentState state, boolean isPaused, String pauseReason) {
        byte[] blob = codec.serialize(state);
        String hash = codec.hash(blob);
        AgentCheckpointEntity latest = mapper.findLatest(runId);
        if (latest != null && hash.equals(latest.getStateHash())) {
            return false; // 相邻相同，去重
        }
        AgentCheckpointEntity e = new AgentCheckpointEntity();
        e.setRunId(runId);
        e.setStepIndex(state.stepIndex());
        e.setStepName(stepName);
        e.setStateBlob(blob);
        e.setStateSize(blob.length);
        e.setStateHash(hash);
        e.setIsPaused(isPaused);
        e.setPauseReason(pauseReason);
        mapper.insert(e);
        return true;
    }

    public Optional<AgentState> loadLatest(String runId) {
        AgentCheckpointEntity latest = mapper.findLatest(runId);
        if (latest == null || latest.getStateBlob() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(codec.deserialize(latest.getStateBlob()));
        } catch (Exception e) {
            log.warn("checkpoint 反序列化失败 runId={}: {}", runId, e.getMessage());
            return Optional.empty();
        }
    }
}
