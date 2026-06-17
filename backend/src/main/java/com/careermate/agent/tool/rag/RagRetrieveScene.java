package com.careermate.agent.tool.rag;

public enum RagRetrieveScene {
    OPPORTUNITY,
    INTERVIEW,
    MARKET,
    COMPANY,
    RESUME,
    GENERAL;

    public static RagRetrieveScene fromValue(String value) {
        if (value == null || value.isBlank()) {
            return GENERAL;
        }
        try {
            return RagRetrieveScene.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return GENERAL;
        }
    }
}
