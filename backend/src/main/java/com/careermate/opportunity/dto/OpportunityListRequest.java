package com.careermate.opportunity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record OpportunityListRequest(
        String keyword,
        String city,
        String position,
        String mode,
        @Min(value = 1, message = "页码需大于等于 1") Integer page,
        @Min(value = 1, message = "每页数量需在 1-20 之间") @Max(value = 20, message = "每页数量需在 1-20 之间") Integer size
) {
    public OpportunityListRequest {
        if (page == null) {
            page = 1;
        }
        if (size == null) {
            size = 10;
        }
    }
}
