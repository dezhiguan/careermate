package com.careermate.resume.version.workflow;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.careermate.mapper.ResumeGenerationRunMapper;
import com.careermate.model.entity.ResumeGenerationRunEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * #5.10：简历生成运行态的持久化与自愈。
 * - 生成开始/成功/失败时落库状态；
 * - 应用启动时把残留的 RUNNING（必然是上次进程崩溃/重启遗留）统一置 FAILED，
 *   使前端不再永久卡"生成中"，可引导重试。
 * 所有写操作对调用方均为尽力而为，失败不影响简历生成主流程。
 */
@Slf4j
@Service
public class ResumeGenerationRunService implements ApplicationRunner {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private final ResumeGenerationRunMapper mapper;

    public ResumeGenerationRunService(ResumeGenerationRunMapper mapper) {
        this.mapper = mapper;
    }

    /** 生成开始，落 RUNNING，返回 runId（失败返回 null，不影响生成）。 */
    public String start(Long userId, String sessionId, String jdId) {
        try {
            OffsetDateTime now = OffsetDateTime.now();
            ResumeGenerationRunEntity e = new ResumeGenerationRunEntity();
            e.setRunId("RGR-" + UUID.randomUUID().toString().replace("-", ""));
            e.setUserId(userId);
            e.setSessionId(sessionId);
            e.setJdId(jdId);
            e.setStatus(STATUS_RUNNING);
            e.setStartedAt(now);
            e.setUpdatedAt(now);
            mapper.insert(e);
            return e.getRunId();
        } catch (Exception ex) {
            log.warn("resume_generation_run start failed: sessionId={}, err={}", sessionId, ex.getMessage());
            return null;
        }
    }

    public void markSuccess(String runId) {
        finish(runId, STATUS_SUCCESS, null);
    }

    public void markFailed(String runId, String error) {
        finish(runId, STATUS_FAILED, error);
    }

    private void finish(String runId, String status, String error) {
        if (runId == null) {
            return;
        }
        try {
            ResumeGenerationRunEntity e = new ResumeGenerationRunEntity();
            e.setRunId(runId);
            e.setStatus(status);
            e.setError(error != null && error.length() > 480 ? error.substring(0, 480) : error);
            e.setUpdatedAt(OffsetDateTime.now());
            mapper.updateById(e);
        } catch (Exception ex) {
            log.warn("resume_generation_run finish failed: runId={}, err={}", runId, ex.getMessage());
        }
    }

    /** 启动自愈：JVM 刚启动，任何 RUNNING 都是上次崩溃遗留 → 置 FAILED。 */
    @Override
    public void run(ApplicationArguments args) {
        try {
            ResumeGenerationRunEntity patch = new ResumeGenerationRunEntity();
            patch.setStatus(STATUS_FAILED);
            patch.setError("服务重启，生成已中断，请重试");
            patch.setUpdatedAt(OffsetDateTime.now());
            int reaped = mapper.update(patch, new LambdaUpdateWrapper<ResumeGenerationRunEntity>()
                    .eq(ResumeGenerationRunEntity::getStatus, STATUS_RUNNING));
            if (reaped > 0) {
                log.warn("启动自愈：{} 条陈旧 RUNNING 简历生成已置 FAILED", reaped);
            }
        } catch (Exception ex) {
            log.warn("resume_generation_run startup reaper failed: {}", ex.getMessage());
        }
    }
}
