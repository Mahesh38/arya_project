package com.smartgrievance.complaint;

import com.smartgrievance.common.PageResult;
import com.smartgrievance.common.TextUtils;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class StaffComplaintRepository {
    private static final String FROM = """
             FROM complaint c JOIN category k ON k.id = c.category_id JOIN area a ON a.id = c.area_id
             LEFT JOIN department d ON d.id = c.department_id
            """;
    private static final String DUE = "TIMESTAMPADD(DAY, k.sla_days, c.created_at)";
    private static final String OVERDUE = "c.status IN ('SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED') AND "
            + DUE + " < :now";
    private final JdbcClient jdbc;

    StaffComplaintRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    PageResult<ComplaintListItem> list(StaffComplaintFilter filter, Long scopeDepartment, LocalDateTime now, int reviewDays) {
        var conditions = conditions(filter, scopeDepartment, now);
        long total = jdbc.sql("SELECT COUNT(*)" + FROM + conditions.where).params(conditions.parameters).query(Long.class).single();
        var page = filter.paging().clampTo(total);
        String order = scopeDepartment == null ? "c.created_at DESC, c.id DESC" : "overdue DESC, c.created_at, c.id";
        var rows = jdbc.sql("""
                SELECT c.complaint_no, c.title, k.name AS category_name, a.name AS area_name,
                       d.name AS department_name, c.status, c.created_at, c.resolved_at,
                """ + DUE + " AS due_at, CASE WHEN " + OVERDUE + " THEN 1 ELSE 0 END AS overdue"
                + FROM + conditions.where + " ORDER BY " + order + " LIMIT :limit OFFSET :offset")
                .params(conditions.parameters).param("now", now).param("limit", page.size()).param("offset", page.offset())
                .query((row, n) -> {
                    var status = ComplaintStatus.valueOf(row.getString("status"));
                    var due = row.getObject("due_at", LocalDateTime.class);
                    var resolved = row.getObject("resolved_at", LocalDateTime.class);
                    boolean overdue = row.getBoolean("overdue");
                    return new ComplaintListItem(row.getString("complaint_no"), row.getString("title"), row.getString("category_name"),
                            row.getString("area_name"), row.getString("department_name"), status,
                            row.getObject("created_at", LocalDateTime.class), overdue, due,
                            overdue ? ComplaintDates.daysOverdue(due, now) : 0,
                            status == ComplaintStatus.RESOLVED && resolved != null ? resolved.plusDays(reviewDays) : null);
                }).list();
        return new PageResult<>(rows, page.page(), page.size(), total);
    }

    /** Number of complaints matching an admin filter (no department scope); checked before the CSV export (D43). */
    long count(StaffComplaintFilter filter, LocalDateTime now) {
        var conditions = conditions(filter, null, now);
        return jdbc.sql("SELECT COUNT(*)" + FROM + conditions.where).params(conditions.parameters).query(Long.class).single();
    }

    record ExportRow(String complaintNo, String title, String category, String area, String address, String department,
                     ComplaintStatus status, LocalDateTime createdAt, LocalDateTime dueAt, boolean overdue,
                     LocalDateTime resolvedAt) {}

    /** Matching rows for the CSV export (R-32), newest first, at most {@code limit}. */
    List<ExportRow> export(StaffComplaintFilter filter, LocalDateTime now, int limit) {
        var conditions = conditions(filter, null, now);
        return jdbc.sql("""
                SELECT c.complaint_no, c.title, k.name AS category_name, a.name AS area_name, c.address,
                       d.name AS department_name, c.status, c.created_at, c.resolved_at,
                """ + DUE + " AS due_at, CASE WHEN " + OVERDUE + " THEN 1 ELSE 0 END AS overdue"
                + FROM + conditions.where + " ORDER BY c.created_at DESC, c.id DESC LIMIT :limit")
                .params(conditions.parameters).param("now", now).param("limit", limit)
                .query((row, n) -> new ExportRow(row.getString("complaint_no"), row.getString("title"),
                        row.getString("category_name"), row.getString("area_name"), row.getString("address"),
                        row.getString("department_name"), ComplaintStatus.valueOf(row.getString("status")),
                        row.getObject("created_at", LocalDateTime.class), row.getObject("due_at", LocalDateTime.class),
                        row.getBoolean("overdue"), row.getObject("resolved_at", LocalDateTime.class))).list();
    }

    private static Filters conditions(StaffComplaintFilter filter, Long scopeDepartment, LocalDateTime now) {
        var conditions = new Filters();
        conditions.add("c.department_id = :scope", "scope", scopeDepartment);
        var statuses = StatusGroup.expand(filter.status());
        if (!statuses.isEmpty() && statuses.size() < ComplaintStatus.values().length) {
            conditions.add("c.status IN (:statuses)", "statuses", statuses.stream().map(Enum::name).toList());
        }
        conditions.add("c.category_id = :category", "category", filter.categoryId());
        conditions.add("c.department_id = :department", "department", filter.departmentId());
        if (filter.unassigned()) conditions.where.append(" AND c.department_id IS NULL");
        conditions.add("c.area_id = :area", "area", filter.areaId());
        if (!filter.invalidDateRange()) {
            if (filter.from() != null) conditions.add("c.created_at >= :from", "from", filter.from().atStartOfDay());
            if (filter.to() != null) conditions.add("c.created_at < :to", "to", filter.to().plusDays(1).atStartOfDay());
        }
        if (filter.q() != null && !filter.q().isBlank()) {
            conditions.add("(c.complaint_no = :number OR LOWER(c.title) LIKE :search ESCAPE '!')", "search", TextUtils.likeContains(filter.q()));
            conditions.parameters.put("number", filter.q().toUpperCase(Locale.ROOT));
        }
        if (filter.overdue()) conditions.add(OVERDUE, "now", now);
        return conditions;
    }

    record StatusCount(ComplaintStatus status, long count, long overdue) {}

    List<StatusCount> departmentCounts(long departmentId, LocalDateTime now) {
        return jdbc.sql("SELECT c.status, COUNT(*) AS total, SUM(CASE WHEN " + OVERDUE + " THEN 1 ELSE 0 END) AS overdue"
                + FROM + " WHERE c.department_id = :department GROUP BY c.status")
                .param("department", departmentId).param("now", now)
                .query((row, index) -> new StatusCount(ComplaintStatus.valueOf(row.getString("status")),
                        row.getLong("total"), row.getLong("overdue"))).list();
    }

    private static class Filters {
        private final StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        private final Map<String, Object> parameters = new LinkedHashMap<>();

        private void add(String condition, String name, Object value) {
            if (value != null) {
                where.append(" AND ").append(condition);
                parameters.put(name, value);
            }
        }
    }
}
