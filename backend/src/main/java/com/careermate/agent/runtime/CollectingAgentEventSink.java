package com.careermate.agent.runtime;

import java.util.ArrayList;
import java.util.List;

public class CollectingAgentEventSink implements AgentEventSink {

    private final List<AgentEvent> events = new ArrayList<>();

    @Override
    public void emit(AgentEvent event) {
        events.add(event);
    }

    public List<AgentEvent> getEvents() {
        return List.copyOf(events);
    }
}
