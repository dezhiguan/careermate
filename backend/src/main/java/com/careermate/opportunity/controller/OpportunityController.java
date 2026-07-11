package com.careermate.opportunity.controller;

import com.careermate.common.api.ApiResponse;
import com.careermate.common.api.PageResult;
import com.careermate.opportunity.dto.OpportunityDetailVO;
import com.careermate.opportunity.dto.OpportunityListItemVO;
import com.careermate.opportunity.dto.OpportunityListRequest;
import com.careermate.opportunity.dto.OpportunityPrepareResponse;
import com.careermate.opportunity.service.OpportunityService;
import com.careermate.security.CurrentUserContext;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/opportunity")
@Validated
public class OpportunityController {

    private final OpportunityService opportunityService;

    public OpportunityController(OpportunityService opportunityService) {
        this.opportunityService = opportunityService;
    }

    @GetMapping("/list")
    public ApiResponse<PageResult<OpportunityListItemVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String position,
            @RequestParam(required = false) String mode,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码需大于等于 1") Integer page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页数量需在 1-20 之间")
            @Max(value = 20, message = "每页数量需在 1-20 之间") Integer size
    ) {
        OpportunityListRequest request = new OpportunityListRequest(keyword, city, position, mode, page, size);
        return ApiResponse.success(opportunityService.list(CurrentUserContext.getUserId(), request));
    }

    @GetMapping("/{jdId}")
    public ApiResponse<OpportunityDetailVO> detail(@PathVariable String jdId) {
        return ApiResponse.success(opportunityService.detail(CurrentUserContext.getUserId(), jdId));
    }

    @PostMapping("/{jdId}/prepare")
    public ApiResponse<OpportunityPrepareResponse> prepare(@PathVariable String jdId) {
        return ApiResponse.success(opportunityService.prepare(CurrentUserContext.getUserId(), jdId));
    }
}
