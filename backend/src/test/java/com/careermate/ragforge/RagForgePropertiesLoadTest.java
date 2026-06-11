package com.careermate.ragforge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("dev")
class RagForgePropertiesLoadTest {

    @Autowired
    private RagForgeProperties properties;

    @Test
    void loadsRagforgeApiKeyFromDotEnv() {
        assertTrue(properties.isEnabled(), "RAGFORGE_ENABLED should be true from .env");
        assertEquals("sk-ragforge-dev", properties.getApiKey());
        assertEquals("16", properties.getJdKbId());
    }
}
