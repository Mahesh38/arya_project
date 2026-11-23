package com.smartgrievance.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartgrievance.housekeeping.HousekeepingJob;
import com.smartgrievance.testsupport.TestDatabase;
import com.smartgrievance.testsupport.FlowTestBase;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "app.housekeeping.batch-size=1")
class HousekeepingTest extends FlowTestBase {
    @Autowired HousekeepingJob job;
    @Autowired JdbcClient jdbc;

    @Test
    @DisplayName("TC-UT-18 housekeeping purges old rows in batches without deleting complaint history")
    void expiresOnlyOlderNotificationsAndOtp() {
        TestDatabase.load(dataSource, "sql/q-fixture.sql");
        var now = LocalDateTime.now(testClock);
        var count = jdbc.sql("SELECT COUNT(*) FROM complaint_event").query(Long.class).single();
        for (int age : List.of(89, 90, 91, 92)) {
            jdbc.sql("INSERT INTO notification(recipient_id, message, created_at) VALUES (4, :message, :sent)")
                    .param("message", "Retention " + age).param("sent", now.minusDays(age)).update();
        }
        for (int age : List.of(29, 30, 31, 32)) {
            var sent = now.minusDays(age);
            jdbc.sql("INSERT INTO mobile_otp(user_id, otp_hash, sent_at, expires_at) VALUES (4, :hash, :sent, :expires)")
                    .param("hash", "fixture-" + age).param("sent", sent).param("expires", sent.plusMinutes(5)).update();
        }
        job.run();
        job.run();
        assertThat(jdbc.sql("SELECT message FROM notification WHERE message LIKE 'Retention %'").query(String.class).list())
                .containsExactlyInAnyOrder("Retention 89", "Retention 90");
        assertThat(jdbc.sql("SELECT otp_hash FROM mobile_otp").query(String.class).list())
                .containsExactlyInAnyOrder("fixture-29", "fixture-30");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM complaint_event").query(Long.class).single()).isEqualTo(count);
    }
}
