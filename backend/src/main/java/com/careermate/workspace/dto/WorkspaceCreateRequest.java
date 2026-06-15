package com.careermate.workspace.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record WorkspaceCreateRequest(
        @NotBlank String workspaceType,
        String title,
        String goalText,
        String entryAction,
        Map<String, Object> contextMetadata
) {
}
