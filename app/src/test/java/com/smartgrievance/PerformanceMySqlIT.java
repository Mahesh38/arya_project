package com.smartgrievance;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartgrievance.seed.ComplaintDemoData;
import com.smartgrievance.seed.DemoData;
import com.smartgrievance.testsupport.HttpTestBase;
import com.smartgrievance.testsupport.HttpTestClient;
import com.smartgrievance.testsupport.TestDatabase;
import com.smartgrievance.user.AccountBootstrap;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * NFR-10 and NFR-12 at 10,000 complaints: the Appendix A demo world plus perf/load-10k.sql (+10,000 complaints,
 * +60,000 events, +30,000 notifications, +2,000 citizens) on MySQL, pages fetched from the real Tomcat.
 * Times go to target/perf-report.txt for the printed test report (doc 14 §11.5).
 */
@Tag("mysql")
@ActiveProfiles({"test", "mysqltest"})
class PerformanceMySqlIT extends HttpTestBase {
    private static final int RUNS = 5;
    private static final long LIMIT_MS = 2_000;
    @Autowired AccountBootstrap bootstrap;
    @Autowired DemoData users;
    @Autowired ComplaintDemoData demo;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void loadTenThousand() {
        bootstrap.ensureAdmin(properties.admin(), false);
        users.seedUsers();
        demo.seed();
        TestDatabase.load(dataSource, "perf/load-10k.sql");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM complaint").query(Long.class).single()).isGreaterThanOrEqualTo(10_000);
    }

    @Test
    @DisplayName("TC-NF-01 main pages answer in under 2 s with 10,000 complaints")
    void pagesStayFast() throws Exception {
        String original = "GRV-%d-000001".formatted(LocalDate.now(testClock).getYear());
        var pages = new LinkedHashMap<String, List<String>>();
        pages.put("mahesh@example.com", List.of("/citizen/complaints", "/citizen/watching", "/complaints/" + original,
                "/notifications"));
        pages.put("anita.deshmukh@example.com", List.of("/officer/queue", "/officer/queue?status=ALL"));
        pages.put("admin@example.com", List.of("/admin/complaints?status=ASSIGNED&page=20", "/admin/complaints?q=load+test",
                "/admin/dashboard", "/admin/complaints/export.csv?status=PENDING"));
        var report = new ArrayList<String>();
        report.add("NFR-10 page times with %d complaints (best / median of %d runs, ms)"
                .formatted(jdbc.sql("SELECT COUNT(*) FROM complaint").query(Long.class).single(), RUNS));
        for (var entry : pages.entrySet()) {
            var client = login(entry.getKey());
            for (String path : entry.getValue()) {
                assertThat(client.get(path).statusCode()).as(path).isEqualTo(200);
                var times = new ArrayList<Long>();
                for (int run = 0; run < RUNS; run++) {
                    long start = System.nanoTime();
                    var response = client.get(path);
                    times.add((System.nanoTime() - start) / 1_000_000);
                    assertThat(response.statusCode()).as(path).isEqualTo(200);
                }
                times.sort(Long::compare);
                long median = times.get(RUNS / 2);
                report.add("%-48s %6d %6d".formatted(path, times.getFirst(), median));
                assertThat(median).as("median time of %s", path).isLessThan(LIMIT_MS);
            }
        }
        Files.write(Path.of("target", "perf-report.txt"), report, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("TC-NF-02 the list, count and timeline queries use their indexes at 10,000 complaints")
    void indexesAreUsed() {
        long owner = jdbc.sql("SELECT id FROM app_user WHERE email = 'mahesh@example.com'").query(Long.class).single();
        long complaint = jdbc.sql("SELECT MIN(id) FROM complaint").query(Long.class).single();
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("SELECT c.id FROM complaint c WHERE c.owner_id = " + owner + " ORDER BY c.created_at DESC LIMIT 10",
                "ix_complaint_owner_created");
        expected.put("SELECT c.id FROM complaint c WHERE c.status IN ('ASSIGNED') ORDER BY c.created_at DESC LIMIT 10 OFFSET 190",
                "ix_complaint_status_created");
        expected.put("SELECT c.id FROM complaint c WHERE c.department_id = 2 AND c.status IN ('ASSIGNED', 'IN_PROGRESS', 'REOPENED', 'RESOLVED')",
                "ix_complaint_dept_status_created");
        expected.put("SELECT c.status, COUNT(*) FROM complaint c GROUP BY c.status", "ix_complaint_status_");
        expected.put("SELECT c.category_id, COUNT(*) FROM complaint c WHERE c.category_id = 3 AND c.status = 'CLOSED' GROUP BY c.category_id",
                "ix_complaint_category_status");
        expected.put("SELECT c.id FROM complaint c WHERE c.status = 'RESOLVED' AND c.resolved_at < '2026-11-16 00:00:00'",
                "ix_complaint_status_resolved");
        expected.put("SELECT COUNT(*) FROM notification c WHERE c.recipient_id = " + owner + " AND c.is_read = FALSE",
                "ix_notification_recipient_read");
        expected.put("SELECT c.id FROM complaint_event c WHERE c.complaint_id = " + complaint + " ORDER BY c.created_at, c.id",
                "ix_event_complaint_created");
        for (var entry : expected.entrySet()) {
            var plan = jdbc.sql("EXPLAIN " + entry.getKey()).query().listOfRows();
            assertThat(plan).as(entry.getKey()).anySatisfy(row ->
                    assertThat(String.valueOf(row.get("key"))).startsWith(entry.getValue()));
        }
        var titleSearch = jdbc.sql("EXPLAIN SELECT c.id FROM complaint c WHERE LOWER(c.title) LIKE '%pothole%'").query().listOfRows();
        assertThat(titleSearch.getFirst().get("type")).as("only the title search scans the table").isEqualTo("ALL");
    }

    private HttpTestClient login(String email) throws Exception {
        var client = new HttpTestClient(port);
        var form = client.get("/login");
        String token = Jsoup.parse(form.body()).select("input[name=_csrf]").attr("value");
        String password = email.startsWith("admin@") ? properties.admin().password() : "Demo@1234";
        String body = "email=" + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&password="
                + URLEncoder.encode(password, StandardCharsets.UTF_8) + "&_csrf=" + token;
        var response = client.send(client.request("/login").header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
        assertThat(response.statusCode()).as("login " + email).isEqualTo(302);
        assertThat(response.headers().firstValue("location").orElse("")).doesNotContain("/login");
        return client;
    }
}
