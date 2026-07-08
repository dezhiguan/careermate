package com.careermate.agent.checkpoint;

import com.careermate.common.api.ApiResponse;
import com.careermate.model.entity.AgentRunEntity;
import com.careermate.security.CurrentUserContext;
import lombok.Data;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * B3：Agent 断点续跑运行——运行历史 / 从检查点续跑 / 分叉重试。默认关（careermate.agent.checkpoint.enabled）。
 */
@RestController
@RequestMapping("/api/agent/checkpoint")
@EnableConfigurationProperties(CheckpointProperties.class)
public class AgentCheckpointController {

    private static final int ERR_DISABLED = 4030;
    private static final int ERR_UNAUTH = 4010;
    private static final int ERR_NOT_FOUND = 4040;

    private final CheckpointTaskService service;

    public AgentCheckpointController(CheckpointTaskService service) {
        this.service = service;
    }

    @PostMapping("/runs")
    public ApiResponse<RunStateVO> start(@RequestBody(required = false) StartReq req) {
        if (!service.isEnabled()) {
            return ApiResponse.fail(ERR_DISABLED, "断点续跑能力尚未开启");
        }
        Long userId = CurrentUserContext.getUserId();
        if (userId == null) {
            return ApiResponse.fail(ERR_UNAUTH, "请先登录后再发起任务");
        }
        String topic = req == null ? null : req.getTopic();
        return ApiResponse.success(RunStateVO.of(service.startRun(userId, topic)));
    }

    @GetMapping("/runs")
    public ApiResponse<List<RunSummaryVO>> list() {
        if (!service.isEnabled()) {
            return ApiResponse.fail(ERR_DISABLED, "断点续跑能力尚未开启");
        }
        Long userId = CurrentUserContext.getUserId();
        if (userId == null) {
            return ApiResponse.success(List.of());
        }
        return ApiResponse.success(service.listRuns(userId).stream().map(RunSummaryVO::of).toList());
    }

    @GetMapping("/runs/{runId}")
    public ApiResponse<RunStateVO> detail(@PathVariable String runId) {
        if (!service.isEnabled()) {
            return ApiResponse.fail(ERR_DISABLED, "断点续跑能力尚未开启");
        }
        Long userId = CurrentUserContext.getUserId();
        Optional<AgentState> state = service.getRunState(userId, runId);
        return state.map(s -> ApiResponse.success(RunStateVO.of(s)))
                .orElseGet(() -> ApiResponse.fail(ERR_NOT_FOUND, "未找到该运行，或你无权访问"));
    }

    @PostMapping("/runs/{runId}/resume")
    public ApiResponse<RunStateVO> resume(@PathVariable String runId, @RequestBody(required = false) ResumeReq req) {
        if (!service.isEnabled()) {
            return ApiResponse.fail(ERR_DISABLED, "断点续跑能力尚未开启");
        }
        Long userId = CurrentUserContext.getUserId();
        String decision = req == null ? null : req.getDecision();
        AgentState resumed = service.resumeRun(userId, runId, decision);
        if (resumed == null) {
            return ApiResponse.fail(ERR_NOT_FOUND, "未找到可续跑的运行，或它已结束/你无权访问");
        }
        return ApiResponse.success(RunStateVO.of(resumed));
    }

    @PostMapping("/runs/{runId}/fork")
    public ApiResponse<ForkVO> fork(@PathVariable String runId) {
        if (!service.isEnabled()) {
            return ApiResponse.fail(ERR_DISABLED, "断点续跑能力尚未开启");
        }
        Long userId = CurrentUserContext.getUserId();
        String newRunId = service.forkRun(userId, runId);
        if (newRunId == null) {
            return ApiResponse.fail(ERR_NOT_FOUND, "无法分叉：运行不存在、无快照或你无权访问");
        }
        ForkVO vo = new ForkVO();
        vo.setRunId(newRunId);
        return ApiResponse.success(vo);
    }

    @Data
    public static class StartReq {
        private String topic;
    }

    @Data
    public static class ResumeReq {
        private String decision;
    }

    @Data
    public static class ForkVO {
        private String runId;
    }

    @Data
    public static class RunStateVO {
        private String runId;
        private int stepIndex;
        private String currentStep;
        private boolean paused;
        private String pendingAction;
        private boolean terminal;
        private java.util.Map<String, Object> data;

        static RunStateVO of(AgentState s) {
            RunStateVO vo = new RunStateVO();
            vo.setRunId(s.runId());
            vo.setStepIndex(s.stepIndex());
            vo.setCurrentStep(s.currentStep());
            vo.setPaused(s.paused());
            vo.setPendingAction(s.pendingAction());
            vo.setTerminal(s.isTerminal());
            vo.setData(s.data());
            return vo;
        }
    }

    @Data
    public static class RunSummaryVO {
        private String runId;
        private String status;
        private String parentRunId;
        private String startedAt;
        private String finishedAt;

        static RunSummaryVO of(AgentRunEntity e) {
            RunSummaryVO vo = new RunSummaryVO();
            vo.setRunId(e.getRunId());
            vo.setStatus(e.getStatus());
            vo.setParentRunId(e.getParentRunId());
            vo.setStartedAt(e.getStartedAt() == null ? null : e.getStartedAt().toString());
            vo.setFinishedAt(e.getFinishedAt() == null ? null : e.getFinishedAt().toString());
            return vo;
        }
    }
}
