package com.careermate.auth.sms;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySmsCodeStore implements SmsCodeStore {

    private final Map<String, Entry> values = new ConcurrentHashMap<>();

    @Override
    public void setValue(String key, String value, Duration ttl) {
        values.put(key, new Entry(value, Instant.now().plus(ttl)));
    }

    @Override
    public Optional<String> getValue(String key) {
        Entry entry = values.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt.isBefore(Instant.now())) {
            values.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value);
    }

    @Override
    public boolean delete(String key) {
        return values.remove(key) != null;
    }

    @Override
    public long increment(String key, Duration ttl) {
        cleanupExpired();
        Entry entry = values.get(key);
        if (entry == null || entry.expiresAt.isBefore(Instant.now())) {
            values.put(key, new Entry("1", Instant.now().plus(ttl)));
            return 1L;
        }
        long next = Long.parseLong(entry.value) + 1L;
        values.put(key, new Entry(String.valueOf(next), entry.expiresAt));
        return next;
    }

    @Override
    public long getCounter(String key) {
        return getValue(key).map(Long::parseLong).orElse(0L);
    }

    @Override
    public Optional<Long> getRemainingTtlSeconds(String key) {
        Entry entry = values.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        long seconds = Duration.between(Instant.now(), entry.expiresAt).getSeconds();
        if (seconds <= 0) {
            values.remove(key);
            return Optional.empty();
        }
        return Optional.of(seconds);
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        values.entrySet().removeIf(entry -> entry.getValue().expiresAt.isBefore(now));
    }

    /** Test-only helper to isolate rate-limit state between cases. */
    void clearForTests() {
        values.clear();
    }

    private record Entry(String value, Instant expiresAt) {
    }
}
