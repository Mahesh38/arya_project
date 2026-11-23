package com.smartgrievance.complaint;

import com.smartgrievance.common.PageResult;
import com.smartgrievance.seed.ComplaintDemoData;
import com.smartgrievance.seed.DemoData;
import com.smartgrievance.testsupport.Html;
import com.smartgrievance.testsupport.TestDatabase;
import com.smartgrievance.testsupport.WebTestBase;
import com.smartgrievance.user.Account;
import com.smartgrievance.user.AccountBootstrap;
import com.smartgrievance.user.AppUserDetailsService;
import com.smartgrievance.user.CurrentUser;
import com.smartgrievance.user.CurrentAccountService;
import com.smartgrievance.user.Role;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MvcResult;

import static com.smartgrievance.testsupport.Logins.appUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StaffComplaintWebTest extends WebTestBase {
    private static final String QUEUE = "/officer/queue";
    private static final String ALL = "/admin/complaints";
    private static final String ANITA = "anita.deshmukh@example.com";
    @Autowired JdbcClient jdbc;
    @Autowired AppUserDetailsService identities;
    @Autowired CurrentAccountService accounts;
    @Autowired AccountBootstrap bootstrap;
    @Autowired DemoData users;
    @Autowired ComplaintDemoData complaints;

    @Test
    @DisplayName("TC-RT-30, TC-Q-06, TC-UC-30a queue is department-scoped and overdue rows sort first")
    void queueOrdersOverdueThenOldestAndPreservesFilters() throws Exception {
        TestDatabase.load(dataSource, "sql/q-fixture.sql");
        testClock.set(LocalDateTime.of(2026, 11, 22, 12, 0));
        var officer = appUser(identities, ANITA);
        var result = mvc.perform(get(QUEUE).with(officer)).andExpect(status().isOk())
                .andExpect(view().name("officer/queue")).andExpect(model().attributeExists("summary")).andReturn();
        assertThat(numbers(result)).containsExactly(no(13), no(12), no(1));
        assertThat(items(result).getFirst().overdue()).isTrue();
        assertThat(items(result).get(1).overdue()).isFalse();
        assertThat(html(result).text("main")).contains("Water Supply", "Overdue first, then oldest first");
        var all = mvc.perform(get(QUEUE).with(officer).param("status", "ALL")).andExpect(status().isOk()).andReturn();
        assertThat(numbers(all)).containsExactlyInAnyOrder(no(1), no(2), no(12), no(13));
        var filtered = mvc.perform(get(QUEUE).with(officer).param("categoryId", "2").param("areaId", "3")
                        .param("q", no(13).toLowerCase(java.util.Locale.ROOT))).andExpect(status().isOk()).andReturn();
        assertThat(numbers(filtered)).containsExactly(no(13));
        for (String complaint : numbers(all)) {
            mvc.perform(get("/complaints/" + complaint).with(officer)).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("TC-RT-31, TC-Q-07, TC-VE-07 admin filters respect dates and literal search text")
    void adminFiltersUseLiteralSearchAndIgnoreInvalidValues() throws Exception {
        TestDatabase.load(dataSource, "sql/q-fixture.sql");
        var admin = appUser(identities, "admin@example.com");
        var q = mvc.perform(get(ALL).with(admin).param("q", "garbage")
                        .param("from", "2026-11-01").param("to", "2026-11-22"))
                .andExpect(status().isOk()).andExpect(view().name("admin/complaints")).andReturn();
        assertThat(numbers(q)).containsExactly(no(3), no(7));
        var wildcard = mvc.perform(get(ALL).with(admin).param("q", "50%")).andExpect(status().isOk()).andReturn();
        assertThat(numbers(wildcard)).containsExactly(no(12));
        var unassigned = mvc.perform(get(ALL).with(admin).param("departmentId", "NONE")
                        .param("status", "PENDING").param("categoryId", "1").param("areaId", "5"))
                .andExpect(status().isOk()).andReturn();
        assertThat(numbers(unassigned)).containsExactly(no(3));
        var swap = mvc.perform(get(ALL).with(admin).param("from", "2026-11-22").param("to", "2026-11-01")
                        .param("status", "FOO").param("categoryId", "abc").param("page", "-1").param("size", "0"))
                .andExpect(status().isOk()).andReturn();
        assertThat(html(swap).text("main")).contains("From date must not be after To date.");
        assertThat(page(swap).totalItems()).isEqualTo(9);
        assertThat(page(swap).size()).isEqualTo(1);
        assertThat(page(swap).page()).isEqualTo(1);
        var overdue = mvc.perform(get(ALL).with(admin).param("overdue", "true")).andExpect(status().isOk()).andReturn();
        assertThat(items(overdue)).isNotEmpty().allMatch(ComplaintListItem::overdue);
    }

    @Test
    @DisplayName("TC-BR-19, TC-BR-20 overdue begins after the due time and is displayed only to staff")
    void overdueIsComputedAtQueryTime() throws Exception {
        TestDatabase.load(dataSource, "sql/q-fixture.sql");
        var due = LocalDateTime.of(2026, 11, 21, 23, 0);
        jdbc.sql("UPDATE complaint SET created_at = :created WHERE complaint_no = :no")
                .param("created", due.minusDays(2)).param("no", no(1)).update();
        var officer = appUser(identities, ANITA);
        testClock.set(due);
        var onTime = mvc.perform(get(QUEUE).with(officer).param("q", no(1))).andExpect(status().isOk()).andReturn();
        assertThat(items(onTime).getFirst().overdue()).isFalse();
        testClock.set(due.plusSeconds(1));
        var late = mvc.perform(get(QUEUE).with(officer).param("q", no(1))).andExpect(status().isOk()).andReturn();
        assertThat(items(late).getFirst().overdue()).isTrue();
        testClock.set(due.plusHours(2));
        var zeroDays = mvc.perform(get(QUEUE).with(officer).param("q", no(1))).andExpect(status().isOk()).andReturn();
        assertThat(html(zeroDays).text("main")).contains("Overdue 0 days");
        testClock.set(due.plusDays(3).plusHours(11));
        var threeDays = mvc.perform(get(QUEUE).with(officer).param("q", no(1))).andExpect(status().isOk()).andReturn();
        assertThat(html(threeDays).text("main")).contains("Overdue 3 days");
        var resolved = mvc.perform(get(ALL).with(appUser(identities, "admin@example.com"))
                .param("status", "RESOLVED").param("overdue", "true")).andExpect(status().isOk()).andReturn();
        assertThat(items(resolved)).isEmpty();
        var detail = mvc.perform(get("/complaints/" + no(1)).with(appUser(identities, "mahesh@example.com")))
                .andExpect(status().isOk()).andReturn();
        assertThat(html(detail).document().select("main .badge-overdue, main .text-bg-danger")).isEmpty();
    }

    @Test
    @DisplayName("TC-UC-30b queue shows awaiting-citizen rows without workflow buttons")
    void queueShowsResolvedRowsAsAwaitingCitizen() throws Exception {
        bootstrap.ensureAdmin(properties.admin(), false);
        users.seedUsers();
        complaints.seed();
        var result = mvc.perform(get(QUEUE).with(appUser(identities, "amit.pawar@example.com")))
                .andExpect(status().isOk()).andReturn();
        assertThat(numbers(result)).containsExactlyInAnyOrder(no(4), no(11));
        assertThat(html(result).text("main")).contains("Awaiting citizen until");
        assertThat(html(result).document().select("tbody button, tbody form")).isEmpty();
    }

    @Test
    @DisplayName("TC-UC-30c officer without a department sees the specified empty-state error")
    void missingDepartmentCannotReadUnscopedQueue() throws Exception {
        users.seedUsers();
        var stored = accounts.find(identities.loadUserByUsername(ANITA).id()).orElseThrow();
        var malformed = new CurrentUser(new Account(stored.id(), stored.fullName(), stored.email(), stored.mobile(),
                stored.passwordHash(), Role.OFFICER, null, null, true, true, stored.version()));
        var result = mvc.perform(get(QUEUE).with(user(malformed))).andExpect(status().isOk()).andReturn();
        assertThat(page(result).totalItems()).isZero();
        assertThat(html(result).text("main")).contains("Your account has no department; contact the admin.");
        assertThat(html(result).document().select("main form")).isEmpty();
    }

    private static String no(int number) { return "GRV-2026-%06d".formatted(number); }
    private static Html html(MvcResult result) throws Exception { return Html.of(result.getResponse().getContentAsString()); }
    private static PageResult<?> page(MvcResult result) { return (PageResult<?>) result.getModelAndView().getModel().get("page"); }
    private static List<ComplaintListItem> items(MvcResult result) { return page(result).items().stream().map(ComplaintListItem.class::cast).toList(); }
    private static List<String> numbers(MvcResult result) { return items(result).stream().map(ComplaintListItem::complaintNo).toList(); }
}
