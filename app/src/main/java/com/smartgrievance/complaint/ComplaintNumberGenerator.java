package com.smartgrievance.complaint;

import java.util.Locale;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ComplaintNumberGenerator {
    private final JdbcClient jdbc;
    public ComplaintNumberGenerator(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int next(int year) {
        jdbc.sql("""
                INSERT INTO complaint_sequence (seq_year, last_number) VALUES (:year, 0)
                ON DUPLICATE KEY UPDATE last_number = last_number
                """).param("year", year).update();
        int number = jdbc.sql("SELECT last_number FROM complaint_sequence WHERE seq_year = :year FOR UPDATE")
                .param("year", year).query(Integer.class).single() + 1;
        jdbc.sql("UPDATE complaint_sequence SET last_number = :number WHERE seq_year = :year")
                .param("year", year).param("number", number).update();
        return number;
    }

    public static String format(int year, int number) {
        if (year < 2000 || year > 9999 || number < 1 || number > 999999) {
            throw new IllegalArgumentException("Complaint number is outside the valid range");
        }
        return String.format(Locale.ROOT, "GRV-%04d-%06d", year, number);
    }
}
