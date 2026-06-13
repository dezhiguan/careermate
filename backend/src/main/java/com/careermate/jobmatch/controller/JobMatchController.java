package com.careermate.jobmatch.controller;

import com.careermate.common.api.ApiResponse;
import com.careermate.jobmatch.dto.JobMatchAnalyzeRequest;
import com.careermate.jobmatch.dto.JobMatchDetailResponse;
import com.careermate.jobmatch.dto.JobMatchListItemResponse;
import com.careermate.jobmatch.service.JobMatchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/job-matches")
public class JobMatchController {

    private final JobMatchService jobMatchService;

    public JobMatchController(JobMatchService jobMatchService) {
        this.jobMatchService = jobMatchService;
    }

    @GetMapping
    public ApiResponse<List<JobMatchListItemResponse>> list() {
        return ApiResponse.success(jobMatchService.listActiveMatches());
    }

    @PostMapping("/analyze")
    public ApiResponse<JobMatchDetailResponse> analyze(@RequestBody @Valid JobMatchAnalyzeRequest request) {
        return ApiResponse.success(jobMatchService.analyzeCurrentUserDefaultResume(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<JobMatchDetailResponse> get(@PathVariable Long id) {
        return ApiResponse.success(jobMatchService.getMatch(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        jobMatchService.deleteMatch(id);
        return ApiResponse.success();
    }

    @GetMapping("/jd-kb-search")
    public ApiResponse<List<com.careermate.jobmatch.dto.JdKbSearchResultItem>> searchJdKb(
            @org.springframework.web.bind.annotation.RequestParam String q,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "5") int topK) {
        return ApiResponse.success(jobMatchService.searchJdKb(q, topK));
    }
}
