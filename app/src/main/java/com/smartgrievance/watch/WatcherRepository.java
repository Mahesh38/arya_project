package com.smartgrievance.watch;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class WatcherRepository {
    private final JdbcClient jdbc;

    WatcherRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Long> activeWatchers(long complaint) {
        return jdbc.sql("SELECT user_id FROM complaint_watcher WHERE complaint_id = :complaint AND active = TRUE")
                .param("complaint", complaint).query(Long.class).list();
    }

    /** Call while holding the relevant complaint row lock. */
    public void activateOrInsert(long complaint, long user, LocalDateTime now) {
        var current = jdbc.sql("""
                SELECT active FROM complaint_watcher WHERE complaint_id = :complaint AND user_id = :user
                """).param("complaint", complaint).param("user", user).query(Boolean.class).optional();
        if (current.orElse(false)) return;
        String sql = current.isEmpty()
                ? """
                  INSERT INTO complaint_watcher (complaint_id, user_id, active, since, updated_at)
                  VALUES (:complaint, :user, TRUE, :now, :now)
                  """
                : """
                  UPDATE complaint_watcher SET active = TRUE, since = :now, updated_at = :now
                  WHERE complaint_id = :complaint AND user_id = :user AND active = FALSE
                  """;
        jdbc.sql(sql).param("complaint", complaint).param("user", user).param("now", now).update();
    }

    public void deactivate(long complaint, long user, LocalDateTime now) {
        jdbc.sql("""
                UPDATE complaint_watcher SET active = FALSE, updated_at = :now
                WHERE complaint_id = :complaint AND user_id = :user AND active = TRUE
                """).param("complaint", complaint).param("user", user).param("now", now).update();
    }
}
