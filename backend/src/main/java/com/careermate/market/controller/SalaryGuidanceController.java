package com.careermate.market.controller;

import com.careermate.common.api.ApiResponse;
import com.careermate.market.dto.SalaryGuidanceVO;
import com.careermate.market.service.SalaryGuidanceService;
import com.careermate.market.support.MarketDefaults;
import com.careermate.security.CurrentUserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 薪酬谈判建议 REST 接口。
 */
@RestController
@RequestMapping("/api/market")
public class SalaryGuidanceController {

    private final SalaryGuidanceService salaryGuidanceService;

    public SalaryGuidanceController(SalaryGuidanceService salaryGuidanceService) {
        this.salaryGuidanceService = salaryGuidanceService;
    }

    /**
     * 结合市场薪资分位与当前用户期望薪资，给出谈判建议。
     */
    @GetMapping("/salary-guidance")
    public ApiResponse<SalaryGuidanceVO> salaryGuidance(
            @RequestParam(defaultValue = MarketDefaults.ROLE) String role,
            @RequestParam(defaultValue = MarketDefaults.CITY) String city,
            @RequestParam(required = false) String years
    ) {
        return ApiResponse.success(
                salaryGuidanceService.getSalaryGuidance(CurrentUserContext.getUserId(), role, city, years));
    }
}
