package com.smartgrievance.dashboard;

import com.smartgrievance.complaint.ComplaintStatus;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Aggregate queries of doc 08 §5.2 and §5.3 (Q08 to Q13). Open means not final, so RESOLVED counts as open in the
 * open/closed tables (D24); the overdue rules use the five open statuses only (BR-65). The status lists are written out
 * in full because {@code NULL NOT IN (...)} is not true for categories or areas without complaints.
 */
@Repository
public class DashboardRepository {
    static final String NOT_FINAL = "c.status IN ('SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED', 'RESOLVED')";
    static final String FINAL = "c.status IN ('CLOSED', 'REJECTED', 'DUPLICATE', 'WITHDRAWN')";
    static final String OPEN = "c.status IN ('SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED')";
    static final String DUE = "TIMESTAMPADD(DAY, k.sla_days, c.created_at)";

    public record CountRow(Long id, String name, long open, long closed) {}
    public record OverdueRow(String complaintNo, String title, ComplaintStatus status, String departmentName,
                             int slaDays, LocalDateTime createdAt, LocalDateTime dueAt) {}

    private final JdbcClient jdbc;

    public DashboardRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Q08: count per status; statuses without complaints are 0. */
    public Map<ComplaintStatus, Long> countByStatus() {
        var counts = new EnumMap<ComplaintStatus, Long>(ComplaintStatus.class);
        for (var status : ComplaintStatus.values()) counts.put(status, 0L);
        jdbc.sql("SELECT c.status, COUNT(*) AS cnt FROM complaint c GROUP BY c.status")
                .query((row, n) -> counts.put(ComplaintStatus.valueOf(row.getString("status")), row.getLong("cnt")))
                .list();
        return counts;
    }

    /** Q09: overdue complaints, all departments. */
    public long countOverdue(LocalDateTime now) {
        return jdbc.sql("SELECT COUNT(*) FROM complaint c JOIN category k ON k.id = c.category_id WHERE "
                        + OPEN + " AND " + DUE + " < :now")
                .param("now", now).query(Long.class).single();
    }

    /** Q09 for one department (officer summary, FR-63). */
    public long countOverdue(LocalDateTime now, long departmentId) {
        return jdbc.sql("SELECT COUNT(*) FROM complaint c JOIN category k ON k.id = c.category_id WHERE "
                        + OPEN + " AND " + DUE + " < :now AND c.department_id = :department")
                .param("now", now).param("department", departmentId).query(Long.class).single();
    }

    /** Q10: one row per category, including categories without complaints. */
    public List<CountRow> byCategory() {
        return openClosed("category", "category_id");
    }

    /** Q12: one row per area, including areas without complaints. */
    public List<CountRow> byArea() {
        return openClosed("area", "area_id");
    }

    /** Q11: departments that have complaints plus a "(Not assigned)" row with a null id; the service adds zero rows. */
    public List<CountRow> byDepartment() {
        return jdbc.sql("SELECT d.id AS id, d.name AS name, SUM(CASE WHEN " + NOT_FINAL + " THEN 1 ELSE 0 END) AS open_cnt,"
                        + " SUM(CASE WHEN " + FINAL + " THEN 1 ELSE 0 END) AS closed_cnt"
                        + " FROM complaint c LEFT JOIN department d ON d.id = c.department_id"
                        + " GROUP BY d.id, d.name ORDER BY d.name")
                .query((row, n) -> new CountRow(row.getObject("id", Long.class), row.getString("name"),
                        row.getLong("open_cnt"), row.getLong("closed_cnt"))).list();
    }

    /** Q13: overdue complaints, earliest due date first; day counts are computed in Java (D23). */
    public List<OverdueRow> overdueList(LocalDateTime now, int limit) {
        return jdbc.sql("""
                        SELECT c.complaint_no, c.title, c.status, d.name AS department_name, k.sla_days, c.created_at,
                        """ + DUE + " AS due_at FROM complaint c JOIN category k ON k.id = c.category_id"
                        + " LEFT JOIN department d ON d.id = c.department_id WHERE " + OPEN + " AND " + DUE + " < :now"
                        + " ORDER BY due_at ASC, c.id ASC LIMIT :limit")
                .param("now", now).param("limit", limit)
                .query((row, n) -> new OverdueRow(row.getString("complaint_no"), row.getString("title"),
                        ComplaintStatus.valueOf(row.getString("status")), row.getString("department_name"),
                        row.getInt("sla_days"), row.getObject("created_at", LocalDateTime.class),
                        row.getObject("due_at", LocalDateTime.class))).list();
    }

    /** All departments in name order, for the zero rows of the by-department table. */
    public List<CountRow> departments() {
        return jdbc.sql("SELECT id, name FROM department ORDER BY name")
                .query((row, n) -> new CountRow(row.getLong("id"), row.getString("name"), 0, 0)).list();
    }

    // The table names below are constants of this class, never user input.
    private List<CountRow> openClosed(String table, String column) {
        return jdbc.sql("SELECT m.id AS id, m.name AS name, SUM(CASE WHEN " + NOT_FINAL + " THEN 1 ELSE 0 END) AS open_cnt,"
                        + " SUM(CASE WHEN " + FINAL + " THEN 1 ELSE 0 END) AS closed_cnt FROM " + table + " m"
                        + " LEFT JOIN complaint c ON c." + column + " = m.id GROUP BY m.id, m.name ORDER BY m.name")
                .query((row, n) -> new CountRow(row.getLong("id"), row.getString("name"),
                        row.getLong("open_cnt"), row.getLong("closed_cnt"))).list();
    }
}
