package com.careermate.jobmatch.dto;

public record JdKbSearchResultItem(
    String filename,
    String contentPreview,
    String citation,
    Double score
) {}
