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
public class InterviewStatsResponse {

    private int totalSessions;
    private int completedSessions;
    private int activeSessions;
    private Integer averageScore;
    private String latestSessionTitle;
    private OffsetDateTime latestUpdatedAt;
}
