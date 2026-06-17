package com.careermate.workspace.dto;

import java.util.Map;

public record ActionAckResponse(
        boolean noop,
        String sseEndpoint,
        Map<String, Object> card
) {
    public ActionAckResponse {
        card = card == null || card.isEmpty() ? null : card;
    }

    public static ActionAckResponse asNoop() {
        return new ActionAckResponse(true, null, null);
    }

    public static ActionAckResponse withSse(String sseEndpoint) {
        return new ActionAckResponse(false, sseEndpoint, null);
    }

    public static ActionAckResponse withCard(Map<String, Object> card) {
        return new ActionAckResponse(true, null, card);
    }
}
