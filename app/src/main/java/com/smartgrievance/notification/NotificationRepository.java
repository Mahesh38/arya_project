package com.smartgrievance.notification;

import com.smartgrievance.common.PageRequest;
import com.smartgrievance.common.PageResult;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class NotificationRepository {
    private static final RowMapper<NotificationItem> ITEM = (row, index) -> new NotificationItem(
            row.getLong("id"), row.getString("message"), row.getBoolean("is_read"),
            row.getObject("created_at", LocalDateTime.class), row.getString("complaint_no"));

    private final JdbcClient jdbc;

    NotificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    long unreadCount(long recipient) {
        return jdbc.sql("SELECT COUNT(*) FROM notification WHERE recipient_id = :recipient AND is_read = FALSE")
                .param("recipient", recipient).query(Long.class).single();
    }

    PageResult<NotificationItem> list(long recipient, PageRequest requested) {
        long count = jdbc.sql("SELECT COUNT(*) FROM notification WHERE recipient_id = :recipient")
                .param("recipient", recipient).query(Long.class).single();
        var page = requested.clampTo(count);
        var items = jdbc.sql("""
                SELECT n.id, n.message, n.is_read, n.created_at, c.complaint_no
                FROM notification n LEFT JOIN complaint c ON c.id = n.complaint_id
                WHERE n.recipient_id = :recipient
                ORDER BY n.created_at DESC, n.id DESC LIMIT :size OFFSET :offset
                """).param("recipient", recipient).param("size", page.size()).param("offset", page.offset())
                .query(ITEM).list();
        return new PageResult<>(items, page.page(), page.size(), count);
    }

    Optional<NotificationItem> find(long id, long recipient) {
        return jdbc.sql("""
                SELECT n.id, n.message, n.is_read, n.created_at, c.complaint_no
                FROM notification n LEFT JOIN complaint c ON c.id = n.complaint_id
                WHERE n.id = :id AND n.recipient_id = :recipient
                """).param("id", id).param("recipient", recipient)
                .query(ITEM).optional();
    }

    void markRead(long id, long recipient, LocalDateTime now) {
        jdbc.sql("""
                UPDATE notification SET is_read = TRUE, read_at = :now
                WHERE id = :id AND recipient_id = :recipient AND is_read = FALSE
                """).param("id", id).param("recipient", recipient).param("now", now).update();
    }

    void markAllRead(long recipient, LocalDateTime now) {
        jdbc.sql("""
                UPDATE notification SET is_read = TRUE, read_at = :now
                WHERE recipient_id = :recipient AND is_read = FALSE
                """).param("recipient", recipient).param("now", now).update();
    }
}
