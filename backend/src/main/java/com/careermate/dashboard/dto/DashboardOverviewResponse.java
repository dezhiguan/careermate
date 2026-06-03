package com.careermate.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverviewResponse {

    private ResumeStatsResponse resumeStats;
    private JobMatchStatsResponse jobMatchStats;
    private InterviewStatsResponse interviewStats;
    private List<DashboardSuggestionResponse> suggestions;
    private List<DashboardActivityResponse> recentActivities;
}
