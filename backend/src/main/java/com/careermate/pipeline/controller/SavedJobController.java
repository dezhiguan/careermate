package com.careermate.pipeline.controller;

import com.careermate.common.api.ApiResponse;
import com.careermate.pipeline.dto.ApplicationVO;
import com.careermate.pipeline.dto.SaveJobRequest;
import com.careermate.pipeline.dto.SavedJobVO;
import com.careermate.pipeline.service.SavedJobService;
import com.careermate.security.CurrentUserContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 暂存区 REST 接口：收藏 JD（不占看板），一键转为机会。
 */
@RestController
@RequestMapping("/api/saved-jobs")
public class SavedJobController {

    private final SavedJobService savedJobService;

    public SavedJobController(SavedJobService savedJobService) {
        this.savedJobService = savedJobService;
    }

    /** 暂存区列表。 */
    @GetMapping
    public ApiResponse<List<SavedJobVO>> list() {
        return ApiResponse.success(savedJobService.list(CurrentUserContext.getUserId()));
    }

    /** 收藏一个 JD（幂等）。 */
    @PostMapping
    public ApiResponse<SavedJobVO> save(@RequestBody SaveJobRequest request) {
        return ApiResponse.success(savedJobService.save(CurrentUserContext.getUserId(), request));
    }

    /** 取消收藏（按 jdDocId）。 */
    @DeleteMapping("/{jdDocId}")
    public ApiResponse<Void> remove(@PathVariable Long jdDocId) {
        savedJobService.removeByJd(CurrentUserContext.getUserId(), jdDocId);
        return ApiResponse.success();
    }

    /** 一键转为机会（进看板 + 移出暂存区）。 */
    @PostMapping("/{jdDocId}/promote")
    public ApiResponse<ApplicationVO> promote(@PathVariable Long jdDocId) {
        return ApiResponse.success(savedJobService.promote(CurrentUserContext.getUserId(), jdDocId));
    }
}
