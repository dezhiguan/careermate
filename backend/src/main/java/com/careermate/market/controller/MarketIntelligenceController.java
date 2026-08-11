package com.careermate.market.controller;

import com.careermate.common.api.ApiResponse;
import com.careermate.market.dto.CompanyInsightVO;
import com.careermate.market.dto.MarketDimensionsVO;
import com.careermate.market.dto.ResumeGapVO;
import com.careermate.market.dto.SalaryInsightVO;
import com.careermate.market.dto.SkillTrendsVO;
import com.careermate.market.service.MarketDimensionService;
import com.careermate.market.service.MarketIntelligenceService;
import com.careermate.market.support.MarketDefaults;
import com.careermate.security.CurrentUserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
public class MarketIntelligenceController {

    private final MarketIntelligenceService marketIntelligenceService;
    private final MarketDimensionService marketDimensionService;

    public MarketIntelligenceController(
            MarketIntelligenceService marketIntelligenceService,
            MarketDimensionService marketDimensionService
    ) {
        this.marketIntelligenceService = marketIntelligenceService;
        this.marketDimensionService = marketDimensionService;
    }

    /** 行情查询维度字典：岗位（含 AI 分组）/ 城市 / 经验 + 默认口径。前端不再硬编码。 */
    @GetMapping("/dimensions")
    public ApiResponse<MarketDimensionsVO> dimensions() {
        return ApiResponse.success(marketDimensionService.dimensions());
    }

    @GetMapping("/salary-insight")
    public ApiResponse<SalaryInsightVO> salaryInsight(
            @RequestParam(defaultValue = MarketDefaults.ROLE) String role,
            @RequestParam(defaultValue = MarketDefaults.CITY) String city,
            // 不给 years 或给「不限」都表示全经验段——不再静默补成 3-5年
            @RequestParam(required = false) String years
    ) {
        return ApiResponse.success(marketIntelligenceService.getSalaryInsight(role, city, years));
    }

    @GetMapping("/skill-trends")
    public ApiResponse<SkillTrendsVO> skillTrends(
            @RequestParam(defaultValue = MarketDefaults.CITY) String city,
            @RequestParam(defaultValue = MarketDefaults.ROLE) String role
    ) {
        return ApiResponse.success(marketIntelligenceService.getSkillTrends(city, role));
    }

    @GetMapping("/resume-gap")
    public ApiResponse<ResumeGapVO> resumeGap(
            @RequestParam(defaultValue = "default") String jdId
    ) {
        return ApiResponse.success(marketIntelligenceService.getResumeGap(CurrentUserContext.getUserId(), jdId));
    }

    @GetMapping("/company-insight")
    public ApiResponse<CompanyInsightVO> companyInsight(@RequestParam String company) {
        return ApiResponse.success(marketIntelligenceService.getCompanyInsight(company));
    }
}
