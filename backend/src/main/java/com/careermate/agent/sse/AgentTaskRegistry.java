package com.careermate.agent.sse;

import com.careermate.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

@Slf4j
@Component
public class AgentTaskRegistry {

    private final ConcurrentHashMap<String, Future<?>> runningTasks = new ConcurrentHashMap<>();

    /**
     * 原子占用 session 任务槽；未完成任务的 session 再次请求将直接 429。
     */
    public void startOrThrow(String sessionId, Future<?> future) {
        Future<?> existing = runningTasks.putIfAbsent(sessionId, future);
        if (existing == null) {
            return;
        }
        if (!existing.isDone() && !existing.isCancelled()) {
            throw new BizException(429, "当前会话已有任务运行中");
        }
        runningTasks.put(sessionId, future);
    }

    public void cancel(String sessionId) {
        Future<?> future = runningTasks.get(sessionId);
        if (future == null) {
            return;
        }
        try {
            future.cancel(true);
        } catch (Exception e) {
            log.debug("Cancel task failed: sessionId={}", sessionId, e);
        }
    }

    public void complete(String sessionId) {
        runningTasks.remove(sessionId);
    }
}
