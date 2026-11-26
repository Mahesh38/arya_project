package com.smartgrievance.testsupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import javax.sql.DataSource;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

public final class TestDatabase {
    static final List<String> TABLES = List.of("notification", "complaint_revision", "complaint_event",
            "attachment", "complaint_watcher", "complaint", "complaint_sequence", "mobile_otp",
            "app_user", "category", "area", "department");
    private static final List<String> IDENTITIES = TABLES.stream()
            .filter(table -> !List.of("complaint_sequence", "complaint_watcher").contains(table)).toList();

    private TestDatabase() {}

    public static void reset(DataSource dataSource) throws SQLException {
        boolean mysql;
        try (var connection = dataSource.getConnection()) {
            mysql = "MySQL".equals(connection.getMetaData().getDatabaseProductName());
        }
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (String table : TABLES) {
            if ("complaint".equals(table)) jdbc.update("DELETE FROM complaint WHERE duplicate_of_id IS NOT NULL");
            jdbc.update("DELETE FROM " + table);
        }
        String restart = mysql ? " AUTO_INCREMENT = 1" : " ALTER COLUMN id RESTART WITH 1";
        for (String table : IDENTITIES) jdbc.execute("ALTER TABLE " + table + restart);
        load(dataSource, "db/migration/V2__reference_data.sql");
    }

    public static void load(DataSource dataSource, String classpath) {
        new ResourceDatabasePopulator(new ClassPathResource(classpath)).execute(dataSource);
    }

    static void emptyUploads(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return;
        try (var paths = Files.walk(directory)) {
            for (Path entry : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (!entry.equals(directory)) Files.delete(entry);
            }
        }
    }
}
