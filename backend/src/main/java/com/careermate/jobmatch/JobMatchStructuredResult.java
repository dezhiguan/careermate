package com.careermate.jobmatch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JobMatchStructuredResult(
    int matchScore,
    String matchLevel,
    List<String> matchedSkills,
    List<String> missingSkills,
    List<String> strengths,
    List<String> risks,
    List<String> suggestions,
    String analysisSummary
) {}
