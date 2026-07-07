package com.careermate.ragforge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "careermate.ragforge.enabled=true",
        "careermate.ragforge.api-key=test-ragforge-api-key",
        "careermate.ragforge.jd-kb-id=16"
})
class RagForgePropertiesLoadTest {

    @Autowired
    private RagForgeProperties properties;

    @Test
    void bindsRagforgeConfigurationProperties() {
        assertTrue(properties.isEnabled(), "ragforge.enabled should bind to RagForgeProperties");
        assertFalse(properties.getApiKey().isBlank(), "ragforge.api-key should bind to RagForgeProperties");
        assertEquals("test-ragforge-api-key", properties.getApiKey());
        assertFalse(properties.getJdKbId().isBlank(), "ragforge.jd-kb-id should bind to RagForgeProperties");
        assertEquals("16", properties.getJdKbId());
    }
}
