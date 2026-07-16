package com.careermate.pipeline.controller;

import com.careermate.common.api.ApiResponse;
import com.careermate.pipeline.dto.ApplicationVO;
import com.careermate.pipeline.dto.ConfirmStageRequest;
import com.careermate.pipeline.dto.CreateApplicationRequest;
import com.careermate.pipeline.dto.PipelineBoardVO;
import com.careermate.pipeline.dto.UpdateStageRequest;
import com.careermate.pipeline.service.PipelineService;
import com.careermate.security.CurrentUserContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 投递看板 REST 接口。
 */
@RestController
@RequestMapping("/api/pipeline")
public class PipelineController {

    private final PipelineService pipelineService;

    public PipelineController(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    /** 看板（按阶段分列）。 */
    @GetMapping("/board")
    public ApiResponse<PipelineBoardVO> board() {
        return ApiResponse.success(pipelineService.getBoard(CurrentUserContext.getUserId()));
    }

    /** 开始一个投递机会（jd_id 去重）。 */
    @PostMapping("/applications")
    public ApiResponse<ApplicationVO> create(@RequestBody CreateApplicationRequest request) {
        return ApiResponse.success(pipelineService.createApplication(CurrentUserContext.getUserId(), request));
    }

    /** 单条详情。 */
    @GetMapping("/applications/{id}")
    public ApiResponse<ApplicationVO> get(@PathVariable Long id) {
        return ApiResponse.success(pipelineService.getApplication(CurrentUserContext.getUserId(), id));
    }

    /** 移动阶段。 */
    @PatchMapping("/applications/{id}/stage")
    public ApiResponse<ApplicationVO> updateStage(@PathVariable Long id, @RequestBody UpdateStageRequest request) {
        String stage = request == null ? null : request.getStage();
        return ApiResponse.success(pipelineService.updateStage(CurrentUserContext.getUserId(), id, stage));
    }

    /** 按 JD 一键确认流转阶段（Layer-2 确认卡的「确认」；无卡时自动建卡）。 */
    @PostMapping("/confirm-stage")
    public ApiResponse<ApplicationVO> confirmStage(@RequestBody ConfirmStageRequest request) {
        ConfirmStageRequest req = request == null ? new ConfirmStageRequest() : request;
        return ApiResponse.success(pipelineService.updateStageByJd(
                CurrentUserContext.getUserId(),
                req.getJdDocId(), req.getStage(), req.getCompany(), req.getRoleTitle()));
    }

    /** 归档（软删）。 */
    @DeleteMapping("/applications/{id}")
    public ApiResponse<Void> archive(@PathVariable Long id) {
        pipelineService.archiveApplication(CurrentUserContext.getUserId(), id);
        return ApiResponse.success();
    }
}
