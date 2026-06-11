package com.careermate.opportunity.dto;

import java.util.List;

public record OpportunityDetailVO(
        String jdId,
        Long docId,
        String company,
        String title,
        String level,
        String city,
        String experienceRange,
        Integer experienceMin,
        Integer experienceMax,
        String education,
        String companySize,
        String publishedAt,
        Integer matchScore,
        String matchTier,
        List<String> matchReasons,
        List<String> skills,
        Double ragScore,
        String externalUrl,
        String jdContent,
        String salaryRange,
        Integer salaryMin,
        Integer salaryMax
) {}
