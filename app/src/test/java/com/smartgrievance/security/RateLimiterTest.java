package com.smartgrievance.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import com.smartgrievance.testsupport.TestClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RateLimiterTest {
    @Test
    @DisplayName("TC-UT-09 the rate limiter rejects a locked key and expires it at the boundary")
    void lockResets() {
        var clock = new TestClock();
        var limiter = new RateLimiter(clock);
        var time = Duration.ofMinutes(15);
        for (int attempt = 1; attempt < 5; attempt++) {
            assertThat(limiter.record("account", 5, time, time).lockedUntil()).isNull();
        }
        assertThat(limiter.record("account", 5, time, time).lockedUntil()).isEqualTo(clock.instant().plus(time));
        assertThat(limiter.locked("account")).isTrue();
        assertThat(limiter.record("account", 5, time, time).admitted()).isFalse();
        clock.advance(time);
        assertThat(limiter.locked("account")).isFalse();
        assertThat(limiter.record("account", 5, time, time).admitted()).isTrue();
    }

    @Test
    @DisplayName("TC-UT-09 the rate limiter caps records and removes expired entries before admitting new keys")
    void capacity() {
        var clock = new TestClock();
        var limiter = new RateLimiter(clock);
        var time = Duration.ofMinutes(15);
        for (int i = 0; i < 10_000; i++) {
            assertThat(limiter.record("key" + i, 5, time, time).admitted()).isTrue();
        }
        assertThat(limiter.locked("next")).isTrue();
        assertThat(limiter.locked("key0")).isFalse();
        assertThat(limiter.record("next", 5, time, time).admitted()).isFalse();
        clock.advance(time.plusSeconds(1));
        assertThat(limiter.locked("next")).isFalse();
        assertThat(limiter.record("next", 5, time, time).admitted()).isTrue();
    }
}
