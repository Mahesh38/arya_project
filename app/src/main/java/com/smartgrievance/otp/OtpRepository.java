package com.smartgrievance.otp;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class OtpRepository {
    record Code(long id, String hash, LocalDateTime sentAt, LocalDateTime expiresAt, int attempts, boolean used) {}

    private final JdbcClient jdbc;

    OtpRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    List<Code> recent(long userId, LocalDateTime cutoff) {
        return jdbc.sql("""
                SELECT id, otp_hash, sent_at, expires_at, attempts, used FROM mobile_otp
                WHERE user_id = :id AND sent_at >= :cutoff ORDER BY sent_at DESC, id DESC
                """).param("id", userId).param("cutoff", cutoff)
                .query((row, index) -> new Code(row.getLong("id"), row.getString("otp_hash"),
                        row.getObject("sent_at", LocalDateTime.class), row.getObject("expires_at", LocalDateTime.class),
                        row.getInt("attempts"), row.getBoolean("used"))).list();
    }

    void issue(long userId, String hash, LocalDateTime now, LocalDateTime expiry) {
        jdbc.sql("UPDATE mobile_otp SET used = TRUE WHERE user_id = :id AND used = FALSE")
                .param("id", userId).update();
        jdbc.sql("""
                INSERT INTO mobile_otp (user_id, otp_hash, sent_at, expires_at)
                VALUES (:id, :hash, :now, :expiry)
                """).param("id", userId).param("hash", hash).param("now", now).param("expiry", expiry).update();
    }

    boolean reserve(long id, int maximum, LocalDateTime now) {
        return jdbc.sql("""
                UPDATE mobile_otp SET attempts = attempts + 1
                WHERE id = :id AND used = FALSE AND attempts < :maximum AND expires_at > :now
                """).param("id", id).param("maximum", maximum).param("now", now).update() == 1;
    }

    void consume(long id) {
        jdbc.sql("UPDATE mobile_otp SET used = TRUE WHERE id = :id").param("id", id).update();
    }
}
