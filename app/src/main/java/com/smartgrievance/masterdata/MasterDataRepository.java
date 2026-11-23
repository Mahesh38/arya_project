package com.smartgrievance.masterdata;

import com.smartgrievance.common.PageRequest;
import com.smartgrievance.common.PageResult;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
class MasterDataRepository {
    record Row(long id, String name, String description, boolean active, Long defaultDepartmentId,
               String defaultDepartmentName, boolean defaultDepartmentActive, Integer slaDays) {}

    record CategoryDefault(Long departmentId) {}

    private final JdbcClient jdbc;

    MasterDataRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    PageResult<Row> list(MasterType type, MasterDataFilter filter) {
        String where = filter.active() == null ? "" : " WHERE m.active = :active";
        long total = withActive(jdbc.sql("SELECT COUNT(*) FROM " + type.table + " m" + where),
                filter.active()).query(Long.class).single();
        PageRequest page = filter.request().clampTo(total);
        var rows = withActive(jdbc.sql(select(type) + where + " ORDER BY m.name, m.id LIMIT :size OFFSET :offset"),
                filter.active()).param("size", page.size()).param("offset", page.offset());
        return new PageResult<>(query(rows).list(), page.page(), page.size(), total);
    }

    Optional<Row> find(MasterType type, long id) {
        return query(jdbc.sql(select(type) + " WHERE m.id = :id").param("id", id)).optional();
    }

    Optional<LookupOption> lock(MasterType type, long id) {
        return lookups(jdbc.sql("SELECT id, name, active FROM " + type.table + " WHERE id = :id FOR UPDATE")
                .param("id", id)).optional();
    }

    Optional<CategoryDefault> lockCategoryDefault(long id) {
        return jdbc.sql("SELECT default_department_id FROM category WHERE id = :id FOR UPDATE")
                .param("id", id).query((row, index) -> new CategoryDefault(
                        row.getObject("default_department_id", Long.class))).optional();
    }

    Optional<String> activeCategoryUsing(long department) {
        return jdbc.sql("""
                SELECT name FROM category WHERE default_department_id = :id AND active = TRUE
                ORDER BY name, id LIMIT 1 FOR UPDATE
                """).param("id", department).query(String.class).optional();
    }

    List<LookupOption> options(MasterType type, boolean onlyActive) {
        return lookups(jdbc.sql("SELECT id, name, active FROM " + type.table
                + (onlyActive ? " WHERE active = TRUE" : "") + " ORDER BY name, id")).list();
    }

    boolean nameTaken(MasterType type, String name, Long id) {
        var query = jdbc.sql("SELECT COUNT(*) FROM " + type.table + " WHERE LOWER(name) = LOWER(:name)"
                + (id == null ? "" : " AND id <> :id")).param("name", name);
        if (id != null) query.param("id", id);
        return query.query(Long.class).single() > 0;
    }

    long save(MasterType type, Long id, NameForm form, LocalDateTime now) {
        boolean category = type == MasterType.CATEGORY;
        String info = category ? "default_department_id, sla_days" : "description";
        String values = category ? ":department, :sla" : ":description";
        String assignments = category ? "default_department_id = :department, sla_days = :sla" : "description = :description";
        String sql = id == null
                ? "INSERT INTO " + type.table + " (name, " + info + ", created_at, updated_at) VALUES (:name, " + values + ", :now, :now)"
                : "UPDATE " + type.table + " SET name = :name, " + assignments + ", updated_at = :now WHERE id = :id";
        var statement = jdbc.sql(sql).param("name", form.getName()).param("now", now);
        if (category) {
            var fields = (CategoryForm) form;
            statement.param("department", fields.getDefaultDepartmentId(), Types.BIGINT).param("sla", fields.getSlaDays());
        } else {
            statement.param("description", ((DescribedForm) form).getDescription(), Types.VARCHAR);
        }
        if (id != null) {
            statement.param("id", id).update();
            return id;
        }
        var key = new GeneratedKeyHolder();
        statement.update(key, "id");
        return key.getKey().longValue();
    }

    void setActive(MasterType type, long id, boolean active, LocalDateTime now) {
        jdbc.sql("UPDATE " + type.table + " SET active = :active, updated_at = :now WHERE id = :id")
                .param("active", active).param("now", now).param("id", id).update();
    }

    private static String select(MasterType type) {
        return type == MasterType.CATEGORY
                ? """
                  SELECT m.id, m.name, NULL AS description, m.active, m.default_department_id,
                         d.name AS department_name, d.active AS department_active, m.sla_days
                  FROM category m LEFT JOIN department d ON d.id = m.default_department_id
                  """
                : """
                  SELECT m.id, m.name, m.description, m.active, NULL AS default_department_id,
                         NULL AS department_name, NULL AS department_active, NULL AS sla_days FROM
                  """ + " " + type.table + " m";
    }

    private static JdbcClient.MappedQuerySpec<Row> query(JdbcClient.StatementSpec statement) {
        return statement.query((row, index) -> new Row(row.getLong("id"), row.getString("name"),
                row.getString("description"), row.getBoolean("active"), row.getObject("default_department_id", Long.class),
                row.getString("department_name"), row.getBoolean("department_active"), row.getObject("sla_days", Integer.class)));
    }

    private static JdbcClient.MappedQuerySpec<LookupOption> lookups(JdbcClient.StatementSpec statement) {
        return statement.query((row, index) -> new LookupOption(row.getLong("id"), row.getString("name"), row.getBoolean("active")));
    }

    private static JdbcClient.StatementSpec withActive(JdbcClient.StatementSpec query, Boolean active) {
        return active == null ? query : query.param("active", active);
    }
}
