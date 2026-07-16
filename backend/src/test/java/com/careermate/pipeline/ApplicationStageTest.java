package com.careermate.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApplicationStageTest {

    @Test
    void fromCodeParsesCaseInsensitively() {
        assertEquals(ApplicationStage.PREPARING, ApplicationStage.fromCode("preparing"));
        assertEquals(ApplicationStage.OFFER, ApplicationStage.fromCode("  OFFER "));
        assertEquals(ApplicationStage.INTERVIEWING, ApplicationStage.fromCode("Interviewing"));
    }

    @Test
    void fromCodeReturnsNullForUnknownOrBlank() {
        assertNull(ApplicationStage.fromCode("HIRED"));
        assertNull(ApplicationStage.fromCode(""));
        assertNull(ApplicationStage.fromCode("   "));
        assertNull(ApplicationStage.fromCode(null));
    }

    @Test
    void labelAndOrderAreStable() {
        assertEquals("准备/投递", ApplicationStage.PREPARING.label());
        assertEquals(1, ApplicationStage.PREPARING.order());
        assertEquals("已结束", ApplicationStage.CLOSED.label());
        assertEquals(5, ApplicationStage.CLOSED.order());
    }
}
