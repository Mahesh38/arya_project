package com.smartgrievance.complaint;

import static com.smartgrievance.testsupport.Logins.appUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.smartgrievance.seed.ComplaintDemoData;
import com.smartgrievance.seed.DemoData;
import com.smartgrievance.testsupport.LogCapture;
import com.smartgrievance.testsupport.WebTestBase;
import com.smartgrievance.user.AccountBootstrap;
import com.smartgrievance.user.AppUserDetailsService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/** R-32 with app.export.max-rows=5, as TC-RT-32 asks; the demo world has 11 complaints. */
@TestPropertySource(properties = "app.export.max-rows=5")
class CsvExportWebTest extends WebTestBase {
    private static final String EXPORT = "/admin/complaints/export.csv";
    @Autowired AccountBootstrap bootstrap;
    @Autowired DemoData users;
    @Autowired ComplaintDemoData demo;
    @Autowired AppUserDetailsService identities;
    @Autowired JdbcClient jdbc;
    @RegisterExtension final LogCapture logs = new LogCapture();

    @BeforeEach
    void seed() {
        bootstrap.ensureAdmin(properties.admin(), false);
        users.seedUsers();
        demo.seed();
    }

    @Test
    @DisplayName("TC-RT-32 export answers with a timestamped CSV and refuses more than max-rows")
    void exportsOrRefuses() throws Exception {
        var admin = appUser(identities, "admin@example.com");
        var ok = mvc.perform(get(EXPORT).with(admin).param("status", "PENDING")).andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.matchesPattern("attachment; filename=\"complaints-\\d{8}-\\d{4}\\.csv\"(; filename\\*=UTF-8''complaints-\\d{8}-\\d{4}\\.csv)?")))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse();
        assertThat(ok.getContentAsByteArray()).startsWith(0xEF, 0xBB, 0xBF);
        assertThat(new String(ok.getContentAsByteArray(), StandardCharsets.UTF_8).split("\r\n")).hasSize(3);

        var refused = mvc.perform(get(EXPORT).with(admin).param("status", "ALL").param("areaId", "abc"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/admin/complaints?status=ALL"))
                .andExpect(flash().attribute("flashError", "Too many rows (11). Narrow the filter to 5 or fewer."))
                .andReturn();
        assertThat(refused.getResponse().getContentAsByteArray()).isEmpty();
        mvc.perform(get(EXPORT).with(appUser(identities, "anita.deshmukh@example.com"))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-SEC-47, TC-SEC-51 export escapes formula cells and contains no email or mobile")
    void escapesAndLeavesOutContactData() throws Exception {
        String number = "GRV-%d-000003".formatted(LocalDate.now(testClock).getYear());
        jdbc.sql("UPDATE complaint SET title = :title, address = :address WHERE complaint_no = :no")
                .param("title", "=HYPERLINK(\"http://x\",\"x\")").param("address", "@SUM(1+1)").param("no", number).update();
        {
            var response = mvc.perform(get(EXPORT).with(appUser(identities, "admin@example.com")).param("q", number))
                    .andExpect(status().isOk()).andReturn().getResponse();
            String body = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
            assertThat(body).contains("\"'=HYPERLINK(\"\"http://x\"\",\"\"x\"\")\"", "\"'@SUM(1+1)\"");
            String header = body.substring(1, body.indexOf("\r\n")).toLowerCase(java.util.Locale.ROOT);
            assertThat(header).doesNotContain("email", "mobile", "phone");
            assertThat(body).doesNotContain("@example.com", "9800000", "Sneha", "Kale");
            assertThat(logs.entries()).map(event -> event.getFormattedMessage())
                    .anySatisfy(line -> assertThat(line).contains("ADMIN_ACTION", "action=export_csv", "rows=1"));
        }
    }
}
