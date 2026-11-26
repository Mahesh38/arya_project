package com.smartgrievance.notification;

import static com.smartgrievance.testsupport.Logins.appUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.smartgrievance.common.PageResult;
import com.smartgrievance.testsupport.TestDatabase;
import com.smartgrievance.testsupport.WebTestBase;
import com.smartgrievance.user.AppUserDetailsService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.smartgrievance.workflow.ComplaintWorkflowService;
import com.smartgrievance.workflow.TransitionRules.Action;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationWebTest extends WebTestBase {
    private static final String OWNER = "mahesh@example.com";
    private static final String OTHER = "jayesh@example.com";
    private static final String INBOX = "/notifications";
    private static final String COUNT = INBOX + "/unread-count";
    @Autowired AppUserDetailsService users;
    @Autowired JdbcClient jdbc;
    @Autowired ComplaintWorkflowService workflow;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach
    void seed() {
        TestDatabase.load(dataSource, "sql/q-fixture.sql");
    }

    @Test
    @DisplayName("TC-RT-74, TC-Q-27 inbox paging and latest first ordering with id tie-break")
    void pagedListIsRecipientScoped() throws Exception {
        jdbc.sql("""
                INSERT INTO notification(recipient_id, message, created_at)
                VALUES (4, 'Newest by id on a tie', '2026-11-22 09:00:00')
                """).update();
        var result = mvc.perform(get(INBOX).param("size", "500").with(appUser(users, OWNER)))
                .andExpect(status().isOk()).andExpect(view().name("notification/list"))
                .andExpect(model().attribute("unreadCount", 4L)).andReturn();
        var page = (PageResult<?>) result.getModelAndView().getModel().get("page");
        assertThat(page.size()).isEqualTo(50);
        assertThat(page.items().stream().map(NotificationItem.class::cast).map(NotificationItem::id))
                .containsExactly(7L, 4L, 3L, 2L, 1L);
    }

    @Test
    @DisplayName("TC-RT-75, TC-SEC-24, TC-Q-24 only a recipient can open and existing read timestamps are retained")
    void openIsScopedAndIdempotent() throws Exception {
        mvc.perform(post(INBOX + "/4/open").with(csrf()).with(appUser(users, OTHER))).andExpect(status().isNotFound());
        assertThat(read(4)).isFalse();
        mvc.perform(post(INBOX + "/4/open").with(csrf()).with(appUser(users, OWNER)))
                .andExpect(redirectedUrl("/complaints/GRV-2026-000001"));
        assertThat(read(4)).isTrue();
        var timestamp = jdbc.sql("SELECT read_at FROM notification WHERE id = 4").query(LocalDateTime.class).single();
        testClock.set(timestamp.plusHours(1));
        mvc.perform(post(INBOX + "/4/open").with(csrf()).with(appUser(users, OWNER)))
                .andExpect(redirectedUrl("/complaints/GRV-2026-000001"));
        assertThat(jdbc.sql("SELECT read_at FROM notification WHERE id = 4").query(LocalDateTime.class).single())
                .isEqualTo(timestamp);
        jdbc.sql("INSERT INTO notification(recipient_id, message, created_at) VALUES (4, 'General notice', '2026-11-23 09:00:00')").update();
        mvc.perform(post(INBOX + "/7/open").with(csrf()).with(appUser(users, OWNER)))
                .andExpect(redirectedUrl(INBOX));
        assertThat(read(7)).isTrue();
    }

    @Test
    @DisplayName("TC-RT-76, TC-UC-60b mark all read affects only the recipient")
    void markAllReadIsScoped() throws Exception {
        mvc.perform(post(INBOX + "/read-all").with(csrf()).with(appUser(users, OWNER)))
                .andExpect(redirectedUrl(INBOX)).andExpect(flash().attribute("flashSuccess", "All notifications marked as read."));
        mvc.perform(get(COUNT).with(appUser(users, OWNER))).andExpect(jsonPath("$.unread").value(0));
        assertThat(read(5)).isFalse();
        assertThat(read(6)).isFalse();
    }

    @Test
    @DisplayName("TC-RT-77, TC-SEC-02, TC-Q-15, TC-UC-60a unread count is private and anonymous polling creates no session")
    void unreadCountIsPrivateAndSessionAware() throws Exception {
        mvc.perform(get(COUNT).with(appUser(users, OWNER))).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.unread").value(3));
        var result = mvc.perform(get(COUNT)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthenticated"))
                .andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    @DisplayName("TC-BR-21 reopening notifies active admins and currently assigned active officers")
    void reopeningExcludesInactiveStaff() throws Exception {
        for (var staff : java.util.List.of(
                java.util.List.of("amit", "OFFICER", "5", "true", "9000000007"),
                java.util.List.of("kiran", "OFFICER", "5", "false", "9000000008"),
                java.util.List.of("inactive-admin", "ADMIN", "0", "false", "9000000009"))) {
            jdbc.sql("""
                    INSERT INTO app_user (full_name, email, mobile, password_hash, role, department_id, active,
                                          mobile_verified, created_at, updated_at)
                    VALUES (:name, :email, :mobile, 'fixture', :role, :department, :active, TRUE,
                            '2026-11-01 09:00:00', '2026-11-01 09:00:00')
                    """).param("name", staff.get(0)).param("email", staff.get(0) + "@example.com").param("mobile", staff.get(4))
                    .param("role", staff.get(1)).param("department", staff.get(2).equals("0") ? null : Integer.valueOf(staff.get(2)))
                    .param("active", Boolean.parseBoolean(staff.get(3))).update();
        }
        mvc.perform(post("/citizen/complaints/GRV-2026-000004/reopen").with(csrf()).with(appUser(users, OWNER))
                        .param("version", "4").param("remark", "The streetlight is still not working."))
                .andExpect(redirectedUrl("/complaints/GRV-2026-000004"));
        assertThat(jdbc.sql("""
                SELECT u.email FROM notification n JOIN app_user u ON u.id = n.recipient_id
                WHERE n.complaint_id = 3
                """).query(String.class).list()).containsExactlyInAnyOrder("admin@example.com", "amit@example.com");
    }

    @Test
    @DisplayName("TC-BR-22, TC-Q-16, TC-Q-31 recipients are deduplicated and persistent duplicates are rejected")
    void resolveDeduplicatesRecipientsAndExcludesActor() throws Exception {
        jdbc.sql("""
                INSERT INTO complaint_watcher (complaint_id, user_id, active, since, updated_at)
                VALUES (1, 4, TRUE, '2026-11-22 10:00:00', '2026-11-22 10:00:00')
                """).update();
        var transaction = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            workflow.perform("GRV-2026-000001", Action.RESOLVE, 3, "The municipal team repaired the water line.",
                    null, java.util.List.of(), users.loadUserByUsername("admin@example.com"));
            long event = jdbc.sql("SELECT MAX(id) FROM complaint_event").query(Long.class).single();
            assertThat(jdbc.sql("SELECT recipient_id FROM notification WHERE event_id = :event")
                    .param("event", event).query(Long.class).list()).containsExactlyInAnyOrder(4L, 5L);
            jdbc.sql("""
                    INSERT INTO notification(recipient_id, complaint_id, event_id, message, created_at)
                    VALUES (4, 1, :event, 'Duplicate fixture', '2026-11-23 10:00:00')
                    """).param("event", event).update();
        })).isInstanceOf(DuplicateKeyException.class);
        assertThat(jdbc.sql("SELECT status FROM complaint WHERE id = 1").query(String.class).single())
                .isEqualTo("IN_PROGRESS");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM notification").query(Long.class).single()).isEqualTo(6);
        assertThat(jdbc.sql("SELECT MAX(id) FROM complaint_event").query(Long.class).single()).isEqualTo(7);
    }

    @Test
    @DisplayName("TC-SEC-49 citizen pages and stored notifications avoid exposing staff identities")
    void citizenPagesStayPrivate() throws Exception {
        for (String path : java.util.List.of("/citizen/complaints", "/citizen/watching", "/citizen/complaints/new",
                "/complaints/GRV-2026-000001", "/complaints/GRV-2026-000004", "/account/password",
                "/citizen/complaints/GRV-2026-000004/close", "/citizen/complaints/GRV-2026-000004/reopen", INBOX)) {
            var response = mvc.perform(get(path).with(appUser(users, OWNER))).andExpect(status().isOk()).andReturn();
            assertThat(response.getResponse().getContentAsString()).as(path)
                    .doesNotContain("Anita Deshmukh", "Suresh Patil", "System Administrator",
                            "anita.deshmukh@example.com", "suresh.patil@example.com", "admin@example.com");
        }
    }

    private boolean read(long id) {
        return jdbc.sql("SELECT is_read FROM notification WHERE id = :id").param("id", id).query(Boolean.class).single();
    }
}
