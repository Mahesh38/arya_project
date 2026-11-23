package com.smartgrievance.common;

import static com.smartgrievance.testsupport.Logins.appUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartgrievance.seed.ComplaintDemoData;
import com.smartgrievance.seed.DemoData;
import com.smartgrievance.testsupport.Html;
import com.smartgrievance.testsupport.WebTestBase;
import com.smartgrievance.user.AccountBootstrap;
import com.smartgrievance.user.AppUserDetailsService;
import java.time.LocalDate;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** NFR-23, D50: dates on the main screens read like 22-Nov-2026 or 22-Nov-2026 03:45 PM, never ISO. */
class DateFormatPagesWebTest extends WebTestBase {
    private static final Pattern CANDIDATE = Pattern.compile("\\b\\d{1,2}-[A-Za-z]{3}-\\d{4}(?: \\d{1,2}:\\d{2}(?: ?[APap][Mm])?)?");
    private static final Pattern EXPECTED = Pattern.compile("\\d{2}-[A-Z][a-z]{2}-\\d{4}( \\d{2}:\\d{2} (AM|PM))?");
    private static final Pattern ISO = Pattern.compile("(?<![\\w-])\\d{4}-\\d{2}-\\d{2}(?:[T ]\\d{2}:\\d{2})?");
    @Autowired AccountBootstrap bootstrap;
    @Autowired DemoData users;
    @Autowired ComplaintDemoData demo;
    @Autowired AppUserDetailsService identities;

    @BeforeEach
    void seed() {
        bootstrap.ensureAdmin(properties.admin(), false);
        users.seedUsers();
        demo.seed();
    }

    @Test
    @DisplayName("TC-NF-07 every date on SCR-10, 20, 30, 40, 41 and 60 uses the dd-MMM-yyyy format")
    void datesUseTheDisplayFormat() throws Exception {
        String original = "GRV-%d-000001".formatted(LocalDate.now(testClock).getYear());
        String[][] pages = {
                {"mahesh@example.com", "/citizen/complaints"},
                {"mahesh@example.com", "/complaints/" + original},
                {"mahesh@example.com", "/notifications"},
                {"anita.deshmukh@example.com", "/officer/queue"},
                {"admin@example.com", "/admin/dashboard"},
                {"admin@example.com", "/admin/complaints"}};
        int dates = 0;
        for (String[] page : pages) {
            var result = mvc.perform(get(page[1]).with(appUser(identities, page[0]))).andExpect(status().isOk()).andReturn();
            String text = Html.of(result).text("main");
            assertThat(ISO.matcher(text).results().map(match -> match.group()).toList()).as(page[1]).isEmpty();
            for (var match : CANDIDATE.matcher(text).results().toList()) {
                assertThat(match.group()).as(page[1]).matches(EXPECTED);
                dates++;
            }
        }
        assertThat(dates).as("the pages show dates at all").isGreaterThan(10);
    }
}
