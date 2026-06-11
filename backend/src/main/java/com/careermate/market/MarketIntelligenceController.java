package com.careermate.market;

import com.careermate.common.api.ApiResponse;
import com.careermate.market.dto.CompanyInsightVO;
import com.careermate.market.dto.ResumeGapVO;
import com.careermate.market.dto.SalaryInsightVO;
import com.careermate.market.dto.SkillTrendsVO;
import com.careermate.security.CurrentUserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
public class MarketIntelligenceController {

    private final MarketIntelligenceService marketIntelligenceService;

    public MarketIntelligenceController(MarketIntelligenceService marketIntelligenceService) {
        this.marketIntelligenceService = marketIntelligenceService;
    }

    @GetMapping("/salary-insight")
    public ApiResponse<SalaryInsightVO> salaryInsight(
            @RequestParam(defaultValue = "Java后端") String role,
            @RequestParam(defaultValue = "广州") String city,
            @RequestParam(defaultValue = "3-5年") String years
    ) {
        return ApiResponse.success(marketIntelligenceService.getSalaryInsight(role, city, years));
    }

    @GetMapping("/skill-trends")
    public ApiResponse<SkillTrendsVO> skillTrends(
            @RequestParam(defaultValue = "Java后端") String role
    ) {
        return ApiResponse.success(marketIntelligenceService.getSkillTrends(role));
    }

    @GetMapping("/resume-gap")
    public ApiResponse<ResumeGapVO> resumeGap() {
        return ApiResponse.success(marketIntelligenceService.getResumeGap(CurrentUserContext.getUserId()));
    }

    @GetMapping("/company-insight")
    public ApiResponse<CompanyInsightVO> companyInsight(@RequestParam String company) {
        return ApiResponse.success(marketIntelligenceService.getCompanyInsight(company));
    }
}
