package com.careermate.jobmatch.dto;

public record JdKbSearchResultItem(
    String filename,
    String content,
    Double score
) {}
