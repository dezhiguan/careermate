package com.careermate.auth.sms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemorySmsCodeStoreTest {

    private InMemorySmsCodeStore store;

    @BeforeEach
    void setUp() {
        store = new InMemorySmsCodeStore();
    }

    @Test
    void setAndGetValueWithinTtl() {
        store.setValue("key1", "value1", Duration.ofMinutes(5));
        assertTrue(store.getValue("key1").isPresent());
        assertEquals("value1", store.getValue("key1").orElseThrow());
    }

    @Test
    void deleteRemovesValue() {
        store.setValue("key1", "value1", Duration.ofMinutes(5));
        assertTrue(store.delete("key1"));
        assertFalse(store.getValue("key1").isPresent());
    }

    @Test
    void incrementCreatesCounterWithTtl() {
        assertEquals(1L, store.increment("counter", Duration.ofHours(1)));
        assertEquals(2L, store.increment("counter", Duration.ofHours(1)));
        assertEquals(2L, store.getCounter("counter"));
    }

    @Test
    void expiredValueIsNotReturned() throws InterruptedException {
        store.setValue("short", "v", Duration.ofMillis(20));
        Thread.sleep(30);
        assertFalse(store.getValue("short").isPresent());
    }

    @Test
    void remainingTtlReturnsPositiveSeconds() {
        store.setValue("ttl-key", "1", Duration.ofSeconds(60));
        assertTrue(store.getRemainingTtlSeconds("ttl-key").orElse(0L) > 0L);
    }
}
