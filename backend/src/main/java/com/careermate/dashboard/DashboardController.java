package com.careermate.dashboard;

import com.careermate.common.api.ApiResponse;
import com.careermate.dashboard.dto.DashboardOverviewResponse;
import com.careermate.dashboard.dto.SkillGapResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    public ApiResponse<DashboardOverviewResponse> overview() {
        return ApiResponse.success(dashboardService.getOverview());
    }

    @GetMapping("/skill-gap")
    public ResponseEntity<ApiResponse<SkillGapResponse>> getSkillGap() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getSkillGap()));
    }
}
