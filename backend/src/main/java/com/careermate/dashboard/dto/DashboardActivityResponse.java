package com.careermate.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardActivityResponse {

    private String type;
    private String title;
    private String description;
    private String route;
    private OffsetDateTime occurredAt;
}
