package com.careermate.workspace.dto;

public record ActionAckResponse(
        boolean noop,
        String sseEndpoint
) {
    public static ActionAckResponse asNoop() {
        return new ActionAckResponse(true, null);
    }

    public static ActionAckResponse withSse(String sseEndpoint) {
        return new ActionAckResponse(false, sseEndpoint);
    }
}
