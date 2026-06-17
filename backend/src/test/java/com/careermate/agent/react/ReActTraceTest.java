package com.careermate.agent.react;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReActTraceTest {

    @Test
    void hasStepsAndToContextText() {
        ReActTrace trace = new ReActTrace(
                List.of(new ReActStep(1, "思考", "get_default_resume", "观察结果")),
                true,
                1
        );

        assertTrue(trace.hasSteps());
        String text = trace.toContextText();
        assertTrue(text.contains("Round 1"));
        assertTrue(text.contains("Action: get_default_resume"));
        assertTrue(text.contains("Observation: 观察结果"));
        assertFalse(text.contains("Thought"));
    }

    @Test
    void longObservationIsTruncated() {
        String longObs = "x".repeat(400);
        ReActTrace trace = new ReActTrace(
                List.of(new ReActStep(1, "t", "tool", longObs)),
                false,
                1
        );

        String text = trace.toContextText();
        assertTrue(text.contains("..."));
        assertFalse(text.contains(longObs));
    }

    @Test
    void emptyTraceReturnsEmptyContextText() {
        ReActTrace trace = new ReActTrace(List.of(), false, 0);
        assertFalse(trace.hasSteps());
        assertEquals("", trace.toContextText());
    }
}
