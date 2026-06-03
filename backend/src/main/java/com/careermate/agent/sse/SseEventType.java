package com.careermate.agent.sse;

public enum SseEventType {
    INTENT,
    PLAN,
    TOKEN,
    MESSAGE,
    TOOL_CALL,
    TOOL_START,
    TOOL_RESULT,
    TRACE,
    UI_ACTION,
    ERROR,
    DONE,
    HEARTBEAT
}

