package com.smartgrievance.testsupport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;

public record Db(JdbcClient jdbc) {
    public String status(String complaintNo) {
        return jdbc.sql("SELECT status FROM complaint WHERE complaint_no = :no")
                .param("no", complaintNo).query(String.class).single();
    }

    public List<Map<String, Object>> events(String complaintNo) {
        return jdbc.sql("""
                SELECT e.* FROM complaint_event e JOIN complaint c ON c.id = e.complaint_id
                WHERE c.complaint_no = :no ORDER BY e.created_at, e.id
                """).param("no", complaintNo).query().listOfRows();
    }

    public List<Map<String, Object>> notificationsOf(long userId) {
        return jdbc.sql("SELECT * FROM notification WHERE recipient_id = :userId ORDER BY id")
                .param("userId", userId).query().listOfRows();
    }

    public Map<String, Long> rowCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : TestDatabase.TABLES) {
            counts.put(table, jdbc.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single());
        }
        return counts;
    }
}
