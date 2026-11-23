package com.smartgrievance.housekeeping;

import java.time.LocalDateTime;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class HousekeepingRepository {
    private final JdbcClient jdbc;

    HousekeepingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    int deleteNotifications(LocalDateTime cutoff, int limit) {
        return jdbc.sql("DELETE FROM notification WHERE created_at < :cutoff LIMIT :limit")
                .param("cutoff", cutoff).param("limit", limit).update();
    }

    int deleteOtps(LocalDateTime cutoff, int limit) {
        return jdbc.sql("DELETE FROM mobile_otp WHERE sent_at < :cutoff LIMIT :limit")
                .param("cutoff", cutoff).param("limit", limit).update();
    }
}
