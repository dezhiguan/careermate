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
public class JobMatchStatsResponse {

    private int totalMatches;
    private int highMatches;
    private int mediumMatches;
    private int lowMatches;
    private String latestJobTitle;
    private Integer latestMatchScore;
    private OffsetDateTime latestCreatedAt;
}
