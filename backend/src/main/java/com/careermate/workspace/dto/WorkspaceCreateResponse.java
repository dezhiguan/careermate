package com.careermate.workspace.dto;

public record WorkspaceCreateResponse(
        String workspaceId,
        String redirectPath,
        String workspaceType
) {
}
