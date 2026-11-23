package com.smartgrievance.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import com.smartgrievance.testsupport.RepositoryTestBase;
import com.smartgrievance.testsupport.TestDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;

class SchemaRepositoryTest extends RepositoryTestBase {
    @Test
    @DisplayName("TC-NF-09 Flyway applies both migrations and loads reference data")
    void migrationsApply() {
        assertThat(jdbc.sql("SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank")
                .query(String.class).list()).containsExactly("1", "2");
        assertThat(jdbc.sql("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                """).query(String.class).list()).hasSize(13);
        assertThat(new com.smartgrievance.testsupport.Db(jdbc).rowCounts())
                .containsEntry("department", 5L).containsEntry("category", 6L).containsEntry("area", 7L);
    }

    @Test
    @DisplayName("TC-NF-10 named schema constraints are installed")
    void namedConstraintsExist() throws Exception {
        String migration = new ClassPathResource("db/migration/V1__schema.sql").getContentAsString(StandardCharsets.UTF_8);
        List<String> expected = Pattern.compile("CONSTRAINT ((?:fk|uq|ck)_[a-z_]+) ")
                .matcher(migration).results().map(match -> match.group(1)).toList();
        assertThat(expected).isNotEmpty();
        assertThat(jdbc.sql("""
                SELECT constraint_name FROM information_schema.table_constraints WHERE table_schema = 'public'
                """).query(String.class).list()).containsAll(expected);
    }

    @Test
    @DisplayName("TC-Q-25 a resolved complaint must have a resolution timestamp")
    void resolvedWithoutTimestampIsRejected() {
        TestDatabase.load(dataSource, "sql/q-fixture.sql");
        assertThatThrownBy(() -> jdbc.sql("UPDATE complaint SET resolved_at = NULL WHERE complaint_no = :no")
                .param("no", "GRV-2026-000004").update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_complaint_resolved_at");
    }
}
