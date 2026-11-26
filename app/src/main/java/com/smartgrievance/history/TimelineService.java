package com.smartgrievance.history;

import com.smartgrievance.access.ComplaintAccessPolicy;
import com.smartgrievance.complaint.ComplaintStatus;
import com.smartgrievance.complaint.ComplaintVisibility;
import com.smartgrievance.history.ComplaintHistory.Event;
import com.smartgrievance.history.TimelineEntry.FieldChange;
import com.smartgrievance.user.CurrentUser;
import com.smartgrievance.user.Role;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class TimelineService {
    private record Row(long id, Event type, ComplaintStatus from, ComplaintStatus to, String actor,
                       Role role, String department, String relatedNo, long relatedId, long relatedOwner,
                       Long relatedDepartment, String remark, LocalDateTime createdAt) {}
    private final JdbcClient jdbc;
    private final ComplaintVisibility visibility;

    TimelineService(JdbcClient jdbc, ComplaintVisibility visibility) {
        this.jdbc = jdbc;
        this.visibility = visibility;
    }

    public List<TimelineEntry> timeline(long complaintId, boolean owner, CurrentUser viewer) {
        var changes = new HashMap<Long, List<FieldChange>>();
        jdbc.sql("""
                SELECT r.event_id, r.field_name, r.old_value, r.new_value FROM complaint_revision r
                JOIN complaint_event e ON e.id = r.event_id WHERE e.complaint_id = :id ORDER BY r.id
                """).param("id", complaintId).query(rows -> {
                    while (rows.next()) {
                        changes.computeIfAbsent(rows.getLong("event_id"), unused -> new ArrayList<>())
                                .add(new FieldChange(rows.getString("field_name"), rows.getString("old_value"), rows.getString("new_value")));
                    }
                    return changes;
                });
        var rows = jdbc.sql("""
                SELECT e.*, u.full_name, u.role, d.name AS department, r.complaint_no AS related_no,
                       r.owner_id AS related_owner, r.department_id AS related_department
                FROM complaint_event e LEFT JOIN app_user u ON u.id = e.actor_id
                LEFT JOIN department d ON d.id = e.department_id
                LEFT JOIN complaint r ON r.id = e.related_complaint_id
                WHERE e.complaint_id = :id ORDER BY e.created_at, e.id
                """).param("id", complaintId).query((row, index) -> new Row(row.getLong("id"),
                Event.valueOf(row.getString("event_type")), status(row.getString("from_status")),
                status(row.getString("to_status")), row.getString("full_name"), role(row.getString("role")),
                row.getString("department"), row.getString("related_no"), row.getLong("related_complaint_id"),
                row.getLong("related_owner"), row.getObject("related_department", Long.class),
                row.getString("remark"), row.getObject("created_at", LocalDateTime.class))).list();
        var timeline = new ArrayList<TimelineEntry>();
        boolean staff = viewer.role() != Role.CITIZEN;
        String department = null;
        for (var row : rows) {
            if (row.to() == ComplaintStatus.ASSIGNED && row.department() != null) department = row.department();
            String actorDepartment = row.department() == null ? department : row.department();
            boolean redactDuplicate = !staff && row.type() == Event.DUPLICATE_RECEIVED;
            String relatedNo = redactDuplicate ? null : row.relatedNo();
            boolean relatedLinkable = relatedNo != null && ComplaintAccessPolicy.canView(
                    visibility.relation(viewer, row.relatedId(), row.relatedOwner(), row.relatedDepartment()));
            timeline.add(new TimelineEntry(row.type(), row.from(), row.to(), label(row, owner, staff, actorDepartment),
                    row.role(), row.department(), relatedNo, relatedLinkable, redactDuplicate ? null : row.remark(), row.createdAt(),
                    List.copyOf(changes.getOrDefault(row.id(), List.of()))));
            if (row.to() == ComplaintStatus.UNDER_REVIEW && row.from() != ComplaintStatus.SUBMITTED) department = null;
        }
        return List.copyOf(timeline);
    }

    private static ComplaintStatus status(String value) {
        return value == null ? null : ComplaintStatus.valueOf(value);
    }

    private static Role role(String value) {
        return value == null ? null : Role.valueOf(value);
    }

    private static String label(Row row, boolean owner, boolean staff, String department) {
        if (row.role() == null) return "System";
        if (staff) {
            String role = switch (row.role()) {
                case CITIZEN -> "Citizen";
                case ADMIN -> "Admin";
                case OFFICER -> department == null ? "Officer" : "Officer, " + department;
            };
            return row.actor() + " (" + role + ")";
        }
        return switch (row.role()) {
            case CITIZEN -> owner ? "You" : "The complainant";
            case ADMIN -> "Grievance Cell";
            case OFFICER -> department == null ? "Department officer" : department + " Dept.";
        };
    }
}
