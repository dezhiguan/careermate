package com.careermate.opportunity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record OpportunityListRequest(
        String keyword,
        String city,
        String position,
        @Min(1) Integer page,
        @Min(1) @Max(20) Integer size
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
