package com.smartgrievance.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

public final class TestClock extends Clock {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Instant START = LocalDateTime.of(2026, 11, 23, 10, 0).atZone(IST).toInstant();
    private final ZoneId zone;
    private final AtomicReference<Instant> current;

    public TestClock() { this(IST, new AtomicReference<>(START)); }

    private TestClock(ZoneId zone, AtomicReference<Instant> current) {
        this.zone = zone;
        this.current = current;
    }

    @Override public ZoneId getZone() { return zone; }
    @Override public Instant instant() { return current.get(); }
    @Override public Clock withZone(ZoneId zone) { return new TestClock(zone, current); }

    public void reset() { current.set(START); }
    public void advance(Duration duration) { current.updateAndGet(instant -> instant.plus(duration)); }
    public void set(LocalDateTime localTime) { current.set(localTime.atZone(zone).toInstant()); }
}
