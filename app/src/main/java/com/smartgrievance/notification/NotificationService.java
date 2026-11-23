package com.smartgrievance.notification;

import java.time.LocalDateTime;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private final JdbcClient jdbc;
    private final NotificationMessages messages;

    public NotificationService(JdbcClient jdbc, NotificationMessages messages) {
        this.jdbc = jdbc;
        this.messages = messages;
    }

    public void submitted(long complaint, long event, long owner, String number, String title, LocalDateTime now) {
        write(complaint, event, owner, now, "notify.submitted.owner", number);
        adminRecipients(complaint, event, now, "notify.submitted.admin", number, NotificationMessages.excerpt(title, 100));
    }

    public void edited(long complaint, long event, String number, LocalDateTime now) {
        adminRecipients(complaint, event, now, "notify.edited", number);
    }

    public void withdrawn(long complaint, long event, Long department, String number, String reason, LocalDateTime now) {
        var recipients = jdbc.sql("""
                SELECT id FROM app_user WHERE active = TRUE AND
                    (role = 'ADMIN' OR (role = 'OFFICER' AND department_id = :department))
                """).param("department", department).query(Long.class).list();
        for (var recipient : recipients) {
            write(complaint, event, recipient, now, "notify.withdrawn", number, NotificationMessages.excerpt(reason, 120));
        }
    }

    private void adminRecipients(long complaint, long event, LocalDateTime now, String key, Object... args) {
        var ids = jdbc.sql("SELECT id FROM app_user WHERE role = 'ADMIN' AND active = TRUE")
                .query(Long.class).list();
        for (var id : ids) write(complaint, event, id, now, key, args);
    }

    private void write(long complaint, long event, long recipient, LocalDateTime now, String key, Object... args) {
        jdbc.sql("""
                INSERT INTO notification (recipient_id, complaint_id, event_id, message, created_at)
                VALUES (:recipient, :complaint, :event, :message, :now)
                """).param("recipient", recipient).param("complaint", complaint).param("event", event)
                .param("message", messages.render(key, args)).param("now", now).update();
    }
}
