package com.careermate.company.controller;

import com.careermate.common.api.ApiResponse;
import com.careermate.company.dto.CompanyAtmosphereVO;
import com.careermate.company.service.CompanyAtmosphereService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公司氛围 REST 接口。
 */
@RestController
@RequestMapping("/api/company")
public class CompanyAtmosphereController {

    private final CompanyAtmosphereService companyAtmosphereService;

    public CompanyAtmosphereController(CompanyAtmosphereService companyAtmosphereService) {
        this.companyAtmosphereService = companyAtmosphereService;
    }

    /**
     * 查询某公司的氛围（工作强度/团队口碑/面试风格/加班信号）。
     *
     * @param company 公司名
     */
    @GetMapping("/atmosphere")
    public ApiResponse<CompanyAtmosphereVO> atmosphere(@RequestParam String company) {
        return ApiResponse.success(companyAtmosphereService.getCompanyAtmosphere(company));
    }
}
