package com.careermate.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSuggestionResponse {

    private String type;
    private String priority;
    private String title;
    private String description;
    private String actionText;
    private String route;
}
