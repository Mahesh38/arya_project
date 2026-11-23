package com.smartgrievance.complaint;

import com.smartgrievance.masterdata.LookupOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ComplaintRepository {
    public enum Lookup {
        CATEGORY("category"), AREA("area"), DEPARTMENT("department");
        private final String table;
        Lookup(String table) { this.table = table; }
    }

    public record Complaint(long id, String number, long ownerId, long categoryId, long areaId, Long departmentId,
                            String title, String description, String address, ComplaintStatus status, int version, int reopenCount,
                            String categoryName, String areaName, String departmentName, LocalDateTime resolvedAt,
                            LocalDateTime createdAt, LocalDateTime updatedAt, String ownerName, int slaDays,
                            String originalNo, String originalTitle, String originalStatus) {}

    private final JdbcClient jdbc;

    public ComplaintRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Optional<Complaint> find(String number, boolean lock) {
        if (lock && jdbc.sql("SELECT id FROM complaint WHERE complaint_no = :number FOR UPDATE")
                .param("number", number).query(Long.class).optional().isEmpty()) return Optional.empty();
        return jdbc.sql("""
                SELECT c.*, a.name AS area_name, k.name AS category_name, k.sla_days,
                       d.name AS department_name, u.full_name AS owner_name,
                       o.complaint_no AS original_no, o.title AS original_title, o.status AS original_status
                FROM complaint c JOIN area a ON a.id = c.area_id JOIN category k ON k.id = c.category_id
                JOIN app_user u ON u.id = c.owner_id LEFT JOIN complaint o ON o.id = c.duplicate_of_id
                LEFT JOIN department d ON d.id = c.department_id WHERE c.complaint_no = :number
                """).param("number", number).query((row, i) -> new Complaint(row.getLong("id"),
                row.getString("complaint_no"), row.getLong("owner_id"), row.getLong("category_id"), row.getLong("area_id"),
                row.getObject("department_id", Long.class), row.getString("title"), row.getString("description"),
                row.getString("address"), ComplaintStatus.valueOf(row.getString("status")), row.getInt("version"), row.getInt("reopen_count"),
                row.getString("category_name"), row.getString("area_name"), row.getString("department_name"),
                row.getObject("resolved_at", LocalDateTime.class), row.getObject("created_at", LocalDateTime.class),
                row.getObject("updated_at", LocalDateTime.class), row.getString("owner_name"), row.getInt("sla_days"),
                row.getString("original_no"), row.getString("original_title"), row.getString("original_status"))).optional();
    }

    boolean lockVerifiedOwner(long owner) {
        return jdbc.sql("""
                SELECT id FROM app_user WHERE id = :id AND role = 'CITIZEN' AND active = TRUE
                AND mobile_verified = TRUE FOR UPDATE
                """).param("id", owner).query(Long.class).optional().isPresent();
    }

    long filedSince(long owner, LocalDateTime cutoff) {
        return jdbc.sql("SELECT COUNT(*) FROM complaint WHERE owner_id = :id AND created_at >= :cutoff")
                .param("id", owner).param("cutoff", cutoff).query(Long.class).single();
    }

    public Optional<LookupOption> lookup(Lookup lookup, long id, boolean lock) {
        return jdbc.sql("SELECT id, name, active FROM " + lookup.table + " WHERE id = :id" + (lock ? " FOR UPDATE" : ""))
                .param("id", id).query((row, i) -> new LookupOption(row.getLong("id"), row.getString("name"),
                        row.getBoolean("active"))).optional();
    }

    public long insert(String number, long owner, ComplaintForm form, LocalDateTime now) {
        var key = new GeneratedKeyHolder();
        formParameters("""
                INSERT INTO complaint (complaint_no, owner_id, category_id, area_id, title, description, address,
                    status, created_at, updated_at)
                VALUES (:no, :owner, :category, :area, :title, :description, :address, 'SUBMITTED', :now, :now)
                """, form).param("no", number).param("owner", owner).param("now", now).update(key, "id");
        return key.getKey().longValue();
    }

    boolean edit(long id, ComplaintEditForm form, LocalDateTime now) {
        return formParameters("""
                UPDATE complaint SET category_id = :category, area_id = :area, title = :title,
                    description = :description, address = :address, updated_at = :now, version = version + 1
                WHERE id = :id AND version = :version
                """, form).param("id", id).param("version", form.getVersion()).param("now", now)
                .update() == 1;
    }

    boolean updateStatus(long id, int version, ComplaintStatus fromStatus, ComplaintStatus toStatus, LocalDateTime now) {
        return jdbc.sql("""
                UPDATE complaint SET status = :toStatus,
                    resolved_at = CASE WHEN :toStatus = 'RESOLVED' THEN :now ELSE resolved_at END,
                    updated_at = :now, version = version + 1
                WHERE id = :id AND version = :version AND status = :fromStatus
                """).param("id", id).param("version", version).param("fromStatus", fromStatus.name())
                .param("toStatus", toStatus.name()).param("now", now).update() == 1;
    }

    public boolean updateWorkflow(Complaint complaint, int version, ComplaintStatus to, Long department,
                                  LocalDateTime resolvedAt, int reopenCount, LocalDateTime now) {
        return jdbc.sql("""
                UPDATE complaint SET status = :to, department_id = :department, resolved_at = :resolved,
                    reopen_count = :reopens, updated_at = :now, version = version + 1
                WHERE id = :id AND version = :version AND status = :from
                """).param("to", to.name()).param("department", department).param("resolved", resolvedAt)
                .param("reopens", reopenCount).param("now", now).param("id", complaint.id())
                .param("version", version).param("from", complaint.status().name()).update() == 1;
    }

    public Long defaultDepartment(long categoryId) {
        return jdbc.sql("""
                SELECT d.id FROM category ca JOIN department d ON d.id = ca.default_department_id
                WHERE ca.id = :id AND d.active = TRUE
                """).param("id", categoryId).query(Long.class).optional().orElse(null);
    }

    public record Expired(long id, String number, int version, Long departmentId, long ownerId, LocalDateTime resolvedAt) {}

    public List<Expired> findExpiredResolved(LocalDateTime cutoff, int limit, Expired after) {
        String cursor = after == null ? "" : " AND (resolved_at > :previous OR (resolved_at = :previous AND id > :previousId))";
        var statement = jdbc.sql("""
                SELECT id, complaint_no, version, department_id, owner_id, resolved_at FROM complaint
                WHERE status = 'RESOLVED' AND resolved_at < :cutoff
                """ + cursor + " ORDER BY resolved_at, id LIMIT :limit").param("cutoff", cutoff).param("limit", limit);
        if (after != null) statement.param("previous", after.resolvedAt()).param("previousId", after.id());
        return statement.query((row, i) -> new Expired(row.getLong("id"), row.getString("complaint_no"),
                row.getInt("version"), row.getObject("department_id", Long.class), row.getLong("owner_id"),
                row.getObject("resolved_at", LocalDateTime.class))).list();
    }

    public boolean autoClose(Expired expired, LocalDateTime now) {
        return updateStatus(expired.id(), expired.version(), ComplaintStatus.RESOLVED, ComplaintStatus.CLOSED, now);
    }

    record ViewerFacts(boolean watching, boolean linked) {}

    ViewerFacts viewerFacts(long complaint, long user) {
        return jdbc.sql("""
                SELECT
                    (SELECT COUNT(*) FROM complaint_watcher
                        WHERE complaint_id = :id AND user_id = :user AND active = TRUE) AS watching,
                    (SELECT COUNT(*) FROM complaint
                        WHERE duplicate_of_id = :id AND owner_id = :user) AS linked
                """).param("id", complaint).param("user", user)
                .query((row, index) -> new ViewerFacts(row.getLong("watching") > 0, row.getLong("linked") > 0)).single();
    }

    int duplicateCount(long complaint) {
        return jdbc.sql("SELECT COUNT(*) FROM complaint WHERE duplicate_of_id = :id")
                .param("id", complaint).query(Integer.class).single();
    }

    boolean hasDuplicates(long complaint) {
        return jdbc.sql("SELECT id FROM complaint WHERE duplicate_of_id = :id LIMIT 1")
                .param("id", complaint).query(Long.class).optional().isPresent();
    }

    private JdbcClient.StatementSpec formParameters(String sql, ComplaintForm form) {
        return jdbc.sql(sql).param("category", form.getCategoryId()).param("area", form.getAreaId())
                .param("title", form.getTitle()).param("description", form.getDescription()).param("address", form.getAddress());
    }
}
