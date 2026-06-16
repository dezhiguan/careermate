package com.careermate.agent.runtime;

@FunctionalInterface
public interface AgentEventSink {

    void emit(AgentEvent event);
}
