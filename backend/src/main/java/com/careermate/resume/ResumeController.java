package com.careermate.resume;

import com.careermate.common.api.ApiResponse;
import com.careermate.resume.dto.ResumeCreateRequest;
import com.careermate.resume.dto.ResumeDetailResponse;
import com.careermate.resume.dto.ResumeListItemResponse;
import com.careermate.resume.dto.ResumeUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @GetMapping
    public ApiResponse<List<ResumeListItemResponse>> list() {
        return ApiResponse.success(resumeService.listActiveResumes());
    }

    @PostMapping
    public ApiResponse<ResumeDetailResponse> create(@RequestBody @Valid ResumeCreateRequest request) {
        return ApiResponse.success(resumeService.createResume(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ResumeDetailResponse> get(@PathVariable Long id) {
        return ApiResponse.success(resumeService.getResume(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<ResumeDetailResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid ResumeUpdateRequest request
    ) {
        return ApiResponse.success(resumeService.updateResume(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        resumeService.deleteResume(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/default")
    public ApiResponse<ResumeDetailResponse> setDefault(@PathVariable Long id) {
        return ApiResponse.success(resumeService.setDefaultResume(id));
    }
}
