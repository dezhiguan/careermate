package com.careermate.ragforge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("dev")
class RagForgePropertiesLoadTest {

    @Autowired
    private RagForgeProperties properties;

    @Value("${RAGFORGE_JD_KB_ID}")
    private String expectedJdKbId;

    @Value("${RAGFORGE_API_KEY}")
    private String expectedApiKey;

    @Test
    void loadsRagforgeApiKeyFromDotEnv() {
        assertTrue(properties.isEnabled(), "RAGFORGE_ENABLED should be true from .env");
        assertFalse(expectedApiKey.isBlank(), "RAGFORGE_API_KEY should be loaded from .env");
        assertEquals(expectedApiKey, properties.getApiKey());
        assertFalse(expectedJdKbId.isBlank(), "RAGFORGE_JD_KB_ID should be loaded from .env");
        assertEquals(expectedJdKbId, properties.getJdKbId());
    }
}
