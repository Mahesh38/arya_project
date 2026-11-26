package com.smartgrievance.history;

import com.smartgrievance.complaint.ComplaintStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ComplaintHistory {
    public enum Event { CREATED, STATUS_CHANGED, REASSIGNED, REMARK, EDITED, DUPLICATE_LINKED, DUPLICATE_RECEIVED, AUTO_CLOSED }
    public record Revision(String field, String before, String after) {}

    private final JdbcClient jdbc;

    public ComplaintHistory(JdbcClient jdbc) { this.jdbc = jdbc; }

    public long record(long complaint, Event type, ComplaintStatus from, ComplaintStatus to, Long actor,
                       Long department, Long related, String remark, LocalDateTime now) {
        var key = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO complaint_event (complaint_id, event_type, from_status, to_status, actor_id,
                    department_id, related_complaint_id, remark, created_at)
                VALUES (:complaint, :type, :from, :to, :actor, :department, :related, :remark, :now)
                """).param("complaint", complaint).param("type", type.name())
                .param("from", from == null ? null : from.name()).param("to", to == null ? null : to.name())
                .param("actor", actor).param("department", department).param("related", related)
                .param("remark", remark).param("now", now).update(key, "id");
        return key.getKey().longValue();
    }

    public void revisions(long event, List<Revision> revisions) {
        for (var revision : revisions) {
            jdbc.sql("""
                    INSERT INTO complaint_revision (event_id, field_name, old_value, new_value)
                    VALUES (:event, :field, :before, :after)
                    """).param("event", event).param("field", revision.field())
                    .param("before", revision.before()).param("after", revision.after()).update();
        }
    }
}
