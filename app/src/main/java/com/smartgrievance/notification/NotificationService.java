package com.smartgrievance.notification;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import com.smartgrievance.workflow.TransitionRules.Action;
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

    public record WorkflowNotice(long complaintId, long eventId, long ownerId, Long departmentId, Long actorId,
                                 String number, String departmentName, String remark, String reviewDeadline,
                                 LocalDateTime time) {}

    public void workflow(Action action, WorkflowNotice notice) {
        var number = notice.number();
        var remark = NotificationMessages.excerpt(notice.remark(), 120);
        switch (action) {
            case START_REVIEW -> send(notice, List.of(notice.ownerId()), "notify.reviewStarted", number);
            case ASSIGN -> {
                send(notice, ownerAndWatchers(notice), "notify.assigned.citizen", number, notice.departmentName());
                send(notice, officers(notice.departmentId()), "notify.assigned.officer", number);
            }
            case REASSIGN -> {
                send(notice, ownerAndWatchers(notice), "notify.reassigned.citizen", number, notice.departmentName(), remark);
                send(notice, officers(notice.departmentId()), "notify.reassigned.officer", number, remark);
            }
            case START_WORK -> send(notice, ownerAndWatchers(notice), "notify.workStarted", number, remark);
            case ADD_REMARK -> send(notice, ownerAndWatchers(notice), "notify.remark", number, remark);
            case RESOLVE -> {
                send(notice, List.of(notice.ownerId()), "notify.resolved.owner", number, remark, notice.reviewDeadline());
                var watchers = new LinkedHashSet<>(ownerAndWatchers(notice));
                watchers.remove(notice.ownerId());
                send(notice, List.copyOf(watchers), "notify.resolved.watcher", number, remark);
            }
            case CLOSE -> send(notice, officers(notice.departmentId()), "notify.closed", number);
            case REOPEN -> {
                var recipients = new LinkedHashSet<>(adminIds());
                recipients.addAll(officers(notice.departmentId()));
                send(notice, List.copyOf(recipients), "notify.reopened", number, remark);
            }
            case SEND_BACK -> send(notice, adminIds(), "notify.sentBack", number, notice.departmentName(), remark);
            case REJECT -> send(notice, ownerAndWatchers(notice), "notify.rejected", number, remark);
            default -> throw new IllegalArgumentException("Unsupported workflow notification: " + action);
        }
    }

    public void duplicateLinked(long duplicate, long event, long owner, List<Long> watchers,
                                String duplicateNumber, String originalNumber, LocalDateTime now) {
        write(duplicate, event, owner, now, "notify.duplicate.owner", duplicateNumber, originalNumber);
        for (long watcher : watchers) {
            if (watcher != owner) write(duplicate, event, watcher, now, "notify.duplicate.watcher", duplicateNumber, originalNumber);
        }
    }

    public void autoClosed(WorkflowNotice notice, int days) {
        var recipients = new LinkedHashSet<>(officers(notice.departmentId()));
        recipients.add(notice.ownerId());
        send(notice, List.copyOf(recipients), "notify.autoClosed", notice.number(), days);
    }

    private void send(WorkflowNotice notice, List<Long> recipients, String key, Object... args) {
        for (Long recipient : recipients) {
            if (!Objects.equals(recipient, notice.actorId())) {
                write(notice.complaintId(), notice.eventId(), recipient, notice.time(), key, args);
            }
        }
    }

    private List<Long> ownerAndWatchers(WorkflowNotice notice) {
        return jdbc.sql("""
                SELECT owner_id AS user_id FROM complaint WHERE id = :complaint
                UNION
                SELECT user_id FROM complaint_watcher WHERE complaint_id = :complaint AND active = TRUE
                """).param("complaint", notice.complaintId()).query(Long.class).list();
    }

    private List<Long> officers(Long department) {
        return jdbc.sql("""
                SELECT id FROM app_user WHERE active = TRUE AND role = 'OFFICER' AND department_id = :department
                """).param("department", department).query(Long.class).list();
    }

    private List<Long> adminIds() {
        return jdbc.sql("SELECT id FROM app_user WHERE role = 'ADMIN' AND active = TRUE").query(Long.class).list();
    }

    private void adminRecipients(long complaint, long event, LocalDateTime now, String key, Object... args) {
        for (var id : adminIds()) write(complaint, event, id, now, key, args);
    }

    private void write(long complaint, long event, long recipient, LocalDateTime now, String key, Object... args) {
        jdbc.sql("""
                INSERT INTO notification (recipient_id, complaint_id, event_id, message, created_at)
                VALUES (:recipient, :complaint, :event, :message, :now)
                """).param("recipient", recipient).param("complaint", complaint).param("event", event)
                .param("message", messages.render(key, args)).param("now", now).update();
    }
}
