package com.smartgrievance.staff;

import com.smartgrievance.common.PageResult;
import com.smartgrievance.user.Role;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class StaffRepository {
    record Entry(UserRow row, Long departmentId) {}

    private static final String USERS = " FROM app_user u";
    private static final String DEPARTMENTS = " LEFT JOIN department d ON d.id = u.department_id";
    private static final String FIELDS = """
            SELECT u.id, u.full_name, u.email, u.mobile, u.role, u.department_id, d.name AS department_name,
                   u.active, u.mobile_verified, u.created_at, u.version
            """;

    private final JdbcClient jdbc;

    StaffRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    PageResult<UserRow> list(Role role, UserFilter filter) {
        StringBuilder where = new StringBuilder(" WHERE u.role = :role");
        var params = new LinkedHashMap<String, Object>();
        params.put("role", role.name());
        if (filter.active() != null) {
            where.append(" AND u.active = :active");
            params.put("active", filter.active());
        }
        if (filter.departmentId() != null) {
            where.append(" AND u.department_id = :departmentId");
            params.put("departmentId", filter.departmentId());
        }
        if (filter.verified() != null) {
            where.append(" AND u.mobile_verified = :verified");
            params.put("verified", filter.verified());
        }
        if (filter.q() != null) {
            where.append(" AND (LOWER(u.full_name) LIKE :q ESCAPE '!' OR u.email LIKE :q ESCAPE '!'");
            if (role == Role.CITIZEN) where.append(" OR u.mobile LIKE :q ESCAPE '!'");
            where.append(")");
            params.put("q", "%" + filter.q().toLowerCase(Locale.ROOT).replace("!", "!!")
                    .replace("%", "!%").replace("_", "!_") + "%");
        }
        long total = jdbc.sql("SELECT COUNT(*)" + USERS + where).params(params).query(Long.class).single();
        var page = filter.request().clampTo(total);
        var rows = entries(jdbc.sql(FIELDS + USERS + DEPARTMENTS + where + " ORDER BY u.full_name, u.id LIMIT :size OFFSET :offset")
                .params(params).param("size", page.size()).param("offset", page.offset()))
                .list().stream().map(Entry::row).toList();
        return new PageResult<>(rows, page.page(), page.size(), total);
    }

    Optional<Entry> find(Role role, long id) {
        return entries(jdbc.sql(FIELDS + USERS + DEPARTMENTS + " WHERE u.role = :role AND u.id = :id")
                .param("id", id).param("role", role.name())).optional();
    }

    List<Long> activeAdmins() {
        return jdbc.sql("SELECT id FROM app_user WHERE role = 'ADMIN' AND active = TRUE ORDER BY id FOR UPDATE")
                .query(Long.class).list();
    }

    boolean hasOtherMobile(long id, String mobile) {
        return jdbc.sql("SELECT COUNT(*) FROM app_user WHERE id <> :id AND mobile = :mobile")
                .param("id", id).param("mobile", mobile).query(Long.class).single() > 0;
    }

    boolean updateOfficer(long id, OfficerEditForm form, LocalDateTime now) {
        return update("""
                UPDATE app_user SET full_name = :name, mobile = :mobile, department_id = :department,
                                    updated_at = :now, version = version + 1
                WHERE id = :id AND role = :role AND version = :version
                """, id, Role.OFFICER, form.getVersion(), now)
                .param("name", form.getFullName()).param("mobile", form.getMobile())
                .param("department", form.getDepartmentId()).update() == 1;
    }

    boolean setActive(Role role, long id, int version, boolean active, LocalDateTime now) {
        return update("""
                UPDATE app_user SET active = :active, updated_at = :now, version = version + 1
                WHERE id = :id AND role = :role AND version = :version
                """, id, role, version, now).param("active", active).update() == 1;
    }

    boolean resetPassword(Role role, long id, int version, String hash, LocalDateTime now) {
        return update("""
                UPDATE app_user SET password_hash = :hash, updated_at = :now, version = version + 1
                WHERE id = :id AND role = :role AND version = :version
                """, id, role, version, now).param("hash", hash).update() == 1;
    }

    boolean verify(long id, int version, LocalDateTime now) {
        return update("""
                UPDATE app_user SET mobile_verified = TRUE, updated_at = :now, version = version + 1
                WHERE id = :id AND role = :role AND version = :version
                """, id, Role.CITIZEN, version, now).update() == 1;
    }

    private JdbcClient.StatementSpec update(String sql, long id, Role role, int version, LocalDateTime now) {
        return jdbc.sql(sql).param("id", id).param("role", role.name()).param("version", version).param("now", now);
    }

    private static JdbcClient.MappedQuerySpec<Entry> entries(JdbcClient.StatementSpec statement) {
        return statement.query((row, ignored) -> new Entry(new UserRow(row.getLong("id"), row.getString("full_name"),
                row.getString("email"), row.getString("mobile"), Role.valueOf(row.getString("role")),
                row.getString("department_name"), row.getBoolean("active"), row.getBoolean("mobile_verified"),
                row.getObject("created_at", LocalDateTime.class), row.getInt("version")),
                row.getObject("department_id", Long.class)));
    }
}
