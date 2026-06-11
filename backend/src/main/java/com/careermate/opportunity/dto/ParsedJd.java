package com.careermate.opportunity.dto;

import java.time.LocalDate;
import java.util.List;

public record ParsedJd(
        String company,
        String title,
        String level,
        String city,
        String experienceRange,
        Integer experienceMin,
        Integer experienceMax,
        String education,
        String companySize,
        LocalDate publishedAt,
        List<String> skills,
        String jobDescription,
        String salaryRange,
        Integer salaryMin,
        Integer salaryMax
) {
    public static ParsedJd empty() {
        return new ParsedJd(
                null, null, null, null,
                null, null, null,
                null, null, null,
                List.of(), null,
                null, null, null
        );
    }
}
