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

    public boolean tryStart(String sessionId, Future<?> future) {
        Future<?> existing = runningTasks.putIfAbsent(sessionId, future);
        if (existing != null && !existing.isDone() && !existing.isCancelled()) {
            throw new BizException(429, "当前会话已有任务运行中");
        }
        if (existing != null) {
            runningTasks.replace(sessionId, future);
        }
        return true;
    }

    public boolean isRunning(String sessionId) {
        Future<?> future = runningTasks.get(sessionId);
        return future != null && !future.isDone() && !future.isCancelled();
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

