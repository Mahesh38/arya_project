package com.smartgrievance.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@Tag("mysql")
@SpringBootTest
@ActiveProfiles({"test", "mysqltest"})
class FlywayMySqlIT {
    @Autowired DataSource dataSource;
    @Autowired Flyway flyway;
    @Autowired JdbcClient jdbc;

    @Test
    @DisplayName("TC-NF-09 MySQL migration applies and checks a mismatched checksum")
    void checksMySqlMigrationHistory() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
        }
        assertThat(jdbc.sql("SELECT table_name FROM information_schema.tables WHERE table_schema = database()")
                .query(String.class).list()).hasSize(13);
        assertThat(flyway.info().applied()).extracting(info -> info.getVersion().getVersion())
                .containsExactly("1", "2");
        int checksum = jdbc.sql("SELECT checksum FROM flyway_schema_history WHERE version = '2'")
                .query(Integer.class).single();
        jdbc.sql("UPDATE flyway_schema_history SET checksum = :checksum WHERE version = '2'")
                .param("checksum", checksum ^ 1).update();
        try {
            assertThatThrownBy(flyway::validate).isInstanceOf(FlywayValidateException.class)
                    .hasMessageContaining("Migration checksum mismatch");
        } finally {
            jdbc.sql("UPDATE flyway_schema_history SET checksum = :checksum WHERE version = '2'")
                    .param("checksum", checksum).update();
        }
    }
}
