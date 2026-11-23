package com.smartgrievance.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

final class RateLimiter {
    private static final int MAX_KEYS = 10_000;
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;

    record Outcome(boolean admitted, Instant lockedUntil) {}

    private record Window(int count, Instant ends, Instant lockedUntil) {
        boolean expired(Instant now) {
            return !ends.isAfter(now) && (lockedUntil == null || !lockedUntil.isAfter(now));
        }
    }

    RateLimiter(Clock clock) {
        this.clock = clock;
    }

    synchronized boolean locked(String key) {
        var entry = windows.get(key);
        if (entry == null && windows.size() >= MAX_KEYS) {
            cleanup();
            return windows.size() >= MAX_KEYS;
        }
        return entry != null && entry.lockedUntil() != null && entry.lockedUntil().isAfter(clock.instant());
    }

    synchronized Outcome record(String key, int limit, Duration span, Duration lock) {
        var now = clock.instant();
        var entry = windows.get(key);
        if (entry != null && entry.lockedUntil() != null && entry.lockedUntil().isAfter(now)) {
            return new Outcome(false, null);
        }
        if (entry == null && windows.size() >= MAX_KEYS) {
            cleanup();
            if (windows.size() >= MAX_KEYS) return new Outcome(false, null);
        }
        boolean newWindow = entry == null || entry.expired(now);
        int count = newWindow ? 1 : entry.count() + 1;
        var ends = newWindow ? now.plus(span) : entry.ends();
        Instant lockedUntil = count >= limit ? (lock.isZero() ? ends : now.plus(lock)) : null;
        windows.put(key, new Window(count, ends, lockedUntil));
        return new Outcome(true, lockedUntil);
    }

    synchronized void clear(String key) { windows.remove(key); }

    synchronized void cleanup() {
        var now = clock.instant();
        windows.values().removeIf(window -> window.expired(now));
    }
}
