package com.smartgrievance.otp;

import java.time.LocalDateTime;

import com.smartgrievance.testsupport.RepositoryTestBase;
import com.smartgrievance.testsupport.TestDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OtpRepositoryTest extends RepositoryTestBase {
    @Test
    @DisplayName("TC-Q-30 OTP statements invalidate old codes and reserve at most three attempts")
    void issuesAndReservesCodes() {
        TestDatabase.load(dataSource, "sql/q-fixture.sql");
        long userId = jdbc.sql("SELECT id FROM app_user WHERE email = :email")
                .param("email", "mahesh@example.com").query(Long.class).single();
        var now = LocalDateTime.of(2026, 11, 22, 12, 0);
        var codes = new OtpRepository(jdbc);
        codes.issue(userId, "historical-digest", now.minusHours(2), now.minusHours(2).plusMinutes(5));
        codes.issue(userId, "superseded-digest", now.minusMinutes(2), now.plusMinutes(3));
        codes.issue(userId, "current-digest", now, now.plusMinutes(5));

        var recent = codes.recent(userId, now.minusHours(1));
        assertThat(recent).extracting(OtpRepository.Code::hash)
                .containsExactly("current-digest", "superseded-digest");
        var current = recent.getFirst();
        var superseded = recent.getLast();
        assertThat(superseded.used()).isTrue();
        assertThat(codes.reserve(superseded.id(), 3, now)).isFalse();
        assertThat(codes.reserve(current.id(), 3, now.plusMinutes(5))).isFalse();
        for (int expected = 1; expected <= 3; expected++) {
            assertThat(codes.reserve(current.id(), 3, now)).isTrue();
            assertThat(codes.recent(userId, now.minusHours(1)).getFirst().attempts()).isEqualTo(expected);
        }
        assertThat(codes.reserve(current.id(), 3, now)).isFalse();

        codes.consume(current.id());
        assertThat(codes.recent(userId, now.minusHours(1)).getFirst().used()).isTrue();
    }
}
