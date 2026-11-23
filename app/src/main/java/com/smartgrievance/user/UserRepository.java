package com.smartgrievance.user;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    private static final String DETAILS = """
            SELECT u.*, d.name AS department_name
            FROM app_user u LEFT JOIN department d ON d.id = u.department_id
            """;
    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Account> byEmail(String email) {
        return find("u.email", email);
    }

    public Optional<Account> byId(long id) {
        return find("u.id", id);
    }

    private Optional<Account> find(String column, Object value) {
        return jdbc.sql(DETAILS + " WHERE " + column + " = :value").param("value", value)
                .query((row, index) -> new Account(row.getLong("id"), row.getString("full_name"),
                        row.getString("email"), row.getString("mobile"), row.getString("password_hash"),
                        Role.valueOf(row.getString("role")), row.getObject("department_id", Long.class),
                        row.getString("department_name"), row.getBoolean("active"),
                        row.getBoolean("mobile_verified"), row.getInt("version"))).optional();
    }

    public boolean mobileTaken(String mobile) {
        return jdbc.sql("SELECT COUNT(*) FROM app_user WHERE mobile = :mobile")
                .param("mobile", mobile).query(Integer.class).single() > 0;
    }

    public boolean adminExists() {
        return jdbc.sql("SELECT COUNT(*) FROM app_user WHERE role = 'ADMIN'").query(Integer.class).single() > 0;
    }

    public long departmentId(String name) {
        return jdbc.sql("SELECT id FROM department WHERE name = :name").param("name", name)
                .query(Long.class).single();
    }

    public long insert(String name, String email, String mobile, String hash, Role role,
                       Long departmentId, boolean active, boolean verified, LocalDateTime now) {
        var key = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO app_user
                    (full_name, email, mobile, password_hash, role, department_id, active,
                     mobile_verified, created_at, updated_at)
                VALUES (:name, :email, :mobile, :hash, :role, :departmentId, :active, :verified, :now, :now)
                """)
                .param("name", name).param("email", email).param("mobile", mobile).param("hash", hash)
                .param("role", role.name()).param("departmentId", departmentId, java.sql.Types.BIGINT)
                .param("active", active).param("verified", verified).param("now", now).update(key, "id");
        return key.getKey().longValue();
    }

    public boolean updateHash(long id, int version, String hash, LocalDateTime now) {
        return jdbc.sql("""
                UPDATE app_user SET password_hash = :hash, version = version + 1, updated_at = :now
                WHERE id = :id AND version = :version AND active = TRUE
                """).param("id", id).param("version", version).param("hash", hash).param("now", now).update() == 1;
    }

    public boolean lockUnverifiedCitizen(long id) {
        return jdbc.sql("""
                SELECT id FROM app_user
                WHERE id = :id AND active = TRUE AND role = 'CITIZEN' AND mobile_verified = FALSE FOR UPDATE
                """).param("id", id).query(Long.class).optional().isPresent();
    }

    public void markVerified(long id, LocalDateTime now) {
        jdbc.sql("""
                UPDATE app_user SET mobile_verified = TRUE, version = version + 1, updated_at = :now
                WHERE id = :id AND mobile_verified = FALSE
                """).param("id", id).param("now", now).update();
    }
}
