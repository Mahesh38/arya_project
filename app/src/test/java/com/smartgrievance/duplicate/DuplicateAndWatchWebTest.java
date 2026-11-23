package com.smartgrievance.duplicate;

import com.smartgrievance.complaint.ComplaintRepository;
import com.smartgrievance.complaint.ComplaintStatus;
import com.smartgrievance.complaint.ComplaintSummary;
import com.smartgrievance.seed.ComplaintDemoData;
import com.smartgrievance.seed.DemoData;
import com.smartgrievance.testsupport.Html;
import com.smartgrievance.testsupport.WebTestBase;
import com.smartgrievance.user.AccountBootstrap;
import com.smartgrievance.user.AppUserDetailsService;
import com.smartgrievance.watch.WatchService;
import com.smartgrievance.workflow.ComplaintWorkflowService;
import com.smartgrievance.workflow.TransitionRules.Action;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import static com.smartgrievance.testsupport.Logins.appUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DuplicateAndWatchWebTest extends WebTestBase {
    private static final String ORIGINAL = "GRV-2026-000001";
    private static final String SEEDED_DUPLICATE = "GRV-2026-000002";
    private static final String SOURCE = "GRV-2026-000005";
    private static final String OFFICER = "anita.deshmukh@example.com";
    private static final String ADMIN = "admin@example.com";
    private static final String LINKED = "jayesh@example.com";
    private static final String OWNER = "mahesh@example.com";
    private static final String WATCHER = "sneha@example.com";
    private static final String INACTIVE = "rahul@example.com";
    private static final String NOTE = "The reports describe the same leak.";
    @Autowired AccountBootstrap bootstrap;
    @Autowired DemoData users;
    @Autowired ComplaintDemoData demo;
    @Autowired AppUserDetailsService identities;
    @Autowired ComplaintRepository complaints;
    @Autowired WatchService watchers;
    @Autowired ComplaintWorkflowService workflow;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void seed() {
        bootstrap.ensureAdmin(properties.admin(), false);
        users.seedUsers();
        demo.seed();
    }

    @ParameterizedTest(name = "TC-SM-17/TC-SM-18/TC-BR-12 {0} can link from {1}")
    @CsvSource({
            "ADMIN,SUBMITTED", "ADMIN,UNDER_REVIEW", "ADMIN,ASSIGNED", "ADMIN,IN_PROGRESS", "ADMIN,REOPENED",
            "OFFICER,ASSIGNED", "OFFICER,IN_PROGRESS", "OFFICER,REOPENED"
    })
    void linksMoveWatchersAndWriteBothEvents(String role, String status) throws Exception {
        var original = complaints.find(ORIGINAL, false).orElseThrow();
        var source = complaints.find(SOURCE, false).orElseThrow();
        Long department = switch (status) {
            case "SUBMITTED", "UNDER_REVIEW" -> null;
            default -> role.equals("ADMIN") ? source.departmentId() : original.departmentId();
        };
        jdbc.sql("UPDATE complaint SET status = :status, department_id = :dept WHERE id = :id")
                .param("status", status).param("dept", department).param("id", source.id()).update();
        jdbc.sql("DELETE FROM complaint_watcher WHERE complaint_id = :id").param("id", original.id()).update();
        watcher(source.id(), WATCHER, true);
        watcher(source.id(), OWNER, true);
        watcher(source.id(), INACTIVE, false);
        watcher(original.id(), WATCHER, false);
        String path = duplicate(role.toLowerCase(java.util.Locale.ROOT), SOURCE);
        String actor = role.equals("ADMIN") ? ADMIN : OFFICER;
        mvc.perform(post(path).with(appUser(identities, actor)).with(csrf())
                        .param("originalNo", ORIGINAL).param("version", Integer.toString(source.version())).param("remark", NOTE))
                .andExpect(redirectedUrl(detail(SOURCE)))
                .andExpect(flash().attribute("flashSuccess", "Linked to " + ORIGINAL + " as a duplicate."));
        var updated = complaints.find(SOURCE, false).orElseThrow();
        assertThat(updated.status()).isEqualTo(ComplaintStatus.DUPLICATE);
        assertThat(updated.originalNo()).isEqualTo(ORIGINAL);
        assertThat(updated.version()).isEqualTo(source.version() + 1);
        assertThat(activeRecipients(original.id())).containsExactlyInAnyOrder(LINKED, WATCHER);
        assertThat(activeRecipients(source.id())).containsExactlyInAnyOrder(OWNER, WATCHER);
        assertThat(jdbc.sql("""
                SELECT event_type, from_status, to_status, remark FROM complaint_event
                WHERE complaint_id = :id AND related_complaint_id = :original ORDER BY id DESC LIMIT 1
                """).param("id", source.id()).param("original", original.id()).query().singleRow())
                .containsEntry("event_type", "DUPLICATE_LINKED").containsEntry("from_status", status)
                .containsEntry("to_status", "DUPLICATE").containsEntry("remark", NOTE);
        assertThat(jdbc.sql("""
                SELECT COUNT(*) FROM complaint_event
                WHERE complaint_id = :id AND related_complaint_id = :source AND event_type = 'DUPLICATE_RECEIVED'
                """).param("id", original.id()).param("source", source.id()).query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("""
                SELECT u.email FROM notification n JOIN app_user u ON u.id = n.recipient_id
                JOIN complaint_event e ON e.id = n.event_id WHERE e.complaint_id = :id AND e.event_type = 'DUPLICATE_LINKED'
                """).param("id", source.id()).query(String.class).list()).containsExactlyInAnyOrder(OWNER, LINKED, WATCHER);
    }

    @Test
    @DisplayName("TC-RT-54, TC-RT-55, TC-RT-63, TC-RT-64, TC-UC-23a, TC-UC-23d duplicate preview and confirmation use server facts")
    void duplicateFormNormalizesNumberAndValidatesNote() throws Exception {
        var original = complaints.find(ORIGINAL, false).orElseThrow();
        var source = complaints.find(SOURCE, false).orElseThrow();
        jdbc.sql("UPDATE complaint SET department_id = :dept WHERE id = :id")
                .param("dept", original.departmentId()).param("id", source.id()).update();
        String path = duplicate("officer", SOURCE);
        mvc.perform(get(path).with(appUser(identities, OFFICER)))
                .andExpect(status().isOk()).andExpect(view().name("complaint/action-form"))
                .andExpect(model().attribute("duplicateConfirm", false));
        var preview = mvc.perform(get(path).with(appUser(identities, OFFICER)).param("original", " grv-2026-000001 "))
                .andExpect(status().isOk()).andExpect(model().attribute("duplicateConfirm", true)).andReturn();
        var summary = (ComplaintSummary) preview.getModelAndView().getModel().get("original");
        assertThat(summary.title()).isEqualTo(original.title());
        assertThat(summary.areaName()).isEqualTo("Kothrud");
        mvc.perform(post(path).with(appUser(identities, OFFICER)).with(csrf()).param("originalNo", ORIGINAL)
                        .param("version", Integer.toString(source.version())).param("remark", "short"))
                .andExpect(status().isOk()).andExpect(model().attribute("duplicateConfirm", true))
                .andExpect(model().attributeHasFieldErrorCode("form", "remark", "Size"));
        mvc.perform(get(duplicate("admin", SOURCE)).with(appUser(identities, ADMIN)).param("original", ORIGINAL))
                .andExpect(status().isOk()).andExpect(model().attribute("duplicateConfirm", true));
        mvc.perform(post(path).with(appUser(identities, OFFICER)).with(csrf()).param("originalNo", ORIGINAL)
                        .param("version", "999").param("remark", NOTE))
                .andExpect(redirectedUrl(detail(SOURCE)))
                .andExpect(flash().attribute("flashError", containsString("updated by someone else")));
    }

    @Test
    @DisplayName("TC-UC-23b, TC-UC-23c, TC-SM-44, TC-SEC-29, TC-VE-05 refusals disclose only authorized candidate facts")
    void refusesSelfUnknownAlreadyLinkedClosedAndOtherDepartment() throws Exception {
        var original = complaints.find(ORIGINAL, false).orElseThrow();
        var source = complaints.find(SOURCE, false).orElseThrow();
        jdbc.sql("UPDATE complaint SET department_id = :dept WHERE id = :id")
                .param("dept", original.departmentId()).param("id", source.id()).update();
        var path = duplicate("officer", SOURCE);
        var actor = appUser(identities, OFFICER);
        for (var input : List.of(
                List.of(SOURCE, "error.duplicate.self"), List.of(SEEDED_DUPLICATE, "error.duplicate.isDuplicate"),
                List.of("GRV-2026-999999", "error.duplicate.notFound"), List.of("GRV-2026-000004", "error.duplicate.otherDepartment"),
                List.of("", "validation.originalNo.required"), List.of("GRV-INVALID", "validation.originalNo.format"))) {
            var response = mvc.perform(get(path).with(actor).param("original", input.getFirst())).andExpect(status().isOk())
                    .andExpect(model().attribute("duplicateConfirm", false))
                    .andExpect(model().attributeHasFieldErrorCode("form", "originalNo", input.getLast())).andReturn();
            if (input.getLast().equals("error.duplicate.otherDepartment")) {
                assertThat(response.getResponse().getContentAsString()).doesNotContain("Streetlight on ITI Road does not work");
            }
        }
        mvc.perform(get(duplicate("admin", SOURCE)).with(appUser(identities, ADMIN)).param("original", "GRV-2026-000006"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrorCode("form", "originalNo", "error.duplicate.notActive"));
        mvc.perform(post(path).with(actor).with(csrf()).param("originalNo", SOURCE)
                        .param("version", Integer.toString(source.version())).param("remark", NOTE))
                .andExpect(redirectedUrl(detail(SOURCE))).andExpect(flash().attribute("flashError", containsString("itself")));
        assertThat(complaints.find(SOURCE, false).orElseThrow().version()).isEqualTo(source.version());
    }

    @ParameterizedTest(name = "TC-SM-21/TC-SM-22 {0}")
    @EnumSource(ComplaintStatus.class)
    void watchAndUnwatchPreserveHistoryInEveryStatus(ComplaintStatus status) throws Exception {
        var original = complaints.find(ORIGINAL, false).orElseThrow();
        var actor = identities.loadUserByUsername(LINKED);
        long events = count("complaint_event");
        long notices = count("notification");
        jdbc.sql("""
                UPDATE complaint SET status = :status, department_id = :dept, resolved_at = :resolved, duplicate_of_id = :original
                WHERE id = :id
                """).param("id", original.id()).param("status", status.name())
                .param("dept", List.of(ComplaintStatus.SUBMITTED, ComplaintStatus.UNDER_REVIEW).contains(status) ? null : original.departmentId())
                .param("resolved", status == ComplaintStatus.RESOLVED ? LocalDateTime.now(testClock) : null)
                .param("original", status == ComplaintStatus.DUPLICATE ? complaints.find(SOURCE, false).orElseThrow().id() : null).update();
        try {
            assertThat(watchers.unwatch(ORIGINAL, actor)).isTrue();
            assertThat(activeRecipients(original.id())).isEmpty();
            watchers.watch(ORIGINAL, actor);
            assertThat(activeRecipients(original.id())).containsExactly(LINKED);
            assertThat(jdbc.sql("SELECT since FROM complaint_watcher WHERE complaint_id = :id AND user_id = :user")
                    .param("id", original.id()).param("user", actor.id()).query(LocalDateTime.class).single())
                    .isEqualTo(LocalDateTime.now(testClock));
            assertThat(count("complaint_event")).isEqualTo(events);
            assertThat(count("notification")).isEqualTo(notices);
        } finally {
            // This isolated policy fixture allows DUPLICATE as A to exercise the complete matrix.
            jdbc.sql("""
                    UPDATE complaint SET status = :status, department_id = :dept, resolved_at = :resolved,
                        duplicate_of_id = NULL WHERE id = :id
                    """).param("status", original.status().name()).param("dept", original.departmentId())
                    .param("resolved", original.resolvedAt()).param("id", original.id()).update();
        }
    }

    @Test
    @DisplayName("TC-BR-14, TC-BR-16, TC-RT-71, TC-RT-72, TC-UC-17a, TC-UC-17b, TC-Q-28 watcher mutations preserve only legitimate visibility")
    void linkedOwnerKeepsAccessButPlainWatcherDoesNot() throws Exception {
        var original = complaints.find(ORIGINAL, false).orElseThrow();
        var linked = appUser(identities, LINKED);
        mvc.perform(get(detail(SEEDED_DUPLICATE)).with(linked)).andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/complaints/" + ORIGINAL + "\"")));
        mvc.perform(post(watch(ORIGINAL, "unwatch")).with(linked).with(csrf())).andExpect(redirectedUrl(detail(ORIGINAL)))
                .andExpect(flash().attribute("flashSuccess", "You will no longer receive updates."));
        mvc.perform(get("/citizen/watching").with(linked)).andExpect(content().string(containsString("You are not watching any complaints.")));
        mvc.perform(get(detail(ORIGINAL)).with(linked)).andExpect(status().isOk()).andExpect(content().string(containsString("Watch again")));
        mvc.perform(post(watch(ORIGINAL, "unwatch")).with(linked).with(csrf()))
                .andExpect(flash().attribute("flashError", "You are not watching this complaint."));
        mvc.perform(post(watch(ORIGINAL, "watch")).with(linked).with(csrf())).andExpect(redirectedUrl(detail(ORIGINAL)))
                .andExpect(flash().attribute("flashSuccess", "You are watching this complaint again."));
        mvc.perform(post(watch(ORIGINAL, "watch")).with(linked).with(csrf()))
                .andExpect(flash().attribute("flashError", "You are already watching this complaint."));
        mvc.perform(post(watch(ORIGINAL, "unwatch")).with(appUser(identities, OWNER)).with(csrf()))
                .andExpect(flash().attribute("flashError", containsString("You filed this complaint")));
        watcher(original.id(), WATCHER, true);
        mvc.perform(post(watch(ORIGINAL, "unwatch")).with(appUser(identities, WATCHER)).with(csrf()))
                .andExpect(redirectedUrl("/citizen/watching"));
        mvc.perform(get(detail(ORIGINAL)).with(appUser(identities, WATCHER))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TC-SEC-29a duplicate-received links respect staff scope and citizens see no duplicate number")
    void receivedEventOnlyLinksAVisibleDuplicate() throws Exception {
        var source = complaints.find(SOURCE, false).orElseThrow();
        mvc.perform(post(duplicate("admin", SOURCE)).with(appUser(identities, ADMIN)).with(csrf())
                .param("originalNo", ORIGINAL).param("version", Integer.toString(source.version())).param("remark", NOTE))
                .andExpect(redirectedUrl(detail(SOURCE)));
        var officer = mvc.perform(get(detail(ORIGINAL)).with(appUser(identities, OFFICER))).andExpect(status().isOk()).andReturn();
        assertThat(Html.of(officer.getResponse().getContentAsString()).text("main")).contains(SOURCE + " linked as a duplicate");
        assertThat(Html.of(officer.getResponse().getContentAsString()).document().select("main a[href='" + detail(SOURCE) + "']")).isEmpty();
        mvc.perform(get(detail(ORIGINAL)).with(appUser(identities, OWNER))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString(SOURCE))));
    }

    @Test
    @DisplayName("TC-SC-03, TC-SC-05, TC-BR-13, TC-BR-15 inherited active watchers get original updates")
    void watchersReceiveOnlyActiveOriginalUpdates() throws Exception {
        var original = complaints.find(ORIGINAL, false).orElseThrow();
        var source = complaints.find(SOURCE, false).orElseThrow();
        watcher(source.id(), WATCHER, true);
        mvc.perform(post(duplicate("admin", SOURCE)).with(appUser(identities, ADMIN)).with(csrf())
                        .param("originalNo", ORIGINAL).param("version", Integer.toString(source.version())).param("remark", NOTE))
                .andExpect(redirectedUrl(detail(SOURCE)));
        var citizen = identities.loadUserByUsername(LINKED);
        var officer = identities.loadUserByUsername(OFFICER);
        watchers.unwatch(ORIGINAL, citizen);
        workflow.perform(ORIGINAL, Action.ADD_REMARK, original.version(), "The crew is checking the repair.",
                null, List.of(), officer);
        assertThat(latestRecipients(original.id())).containsExactlyInAnyOrder(OWNER, WATCHER);
        watchers.watch(ORIGINAL, citizen);
        workflow.perform(ORIGINAL, Action.RESOLVE, original.version() + 1, "The crew has repaired the leak.",
                null, List.of(), officer);
        assertThat(latestRecipients(original.id())).containsExactlyInAnyOrder(OWNER, LINKED, WATCHER);
        assertThat(activeRecipients(source.id())).containsExactly(WATCHER);
        mvc.perform(get(detail(ORIGINAL)).with(appUser(identities, LINKED))).andExpect(status().isOk())
                .andExpect(content().string(containsString(">Unwatch<")))
                .andExpect(content().string(not(containsString(">Close complaint<"))));
        mvc.perform(get(detail(ORIGINAL)).with(appUser(identities, OWNER))).andExpect(status().isOk())
                .andExpect(content().string(containsString(">Close complaint<")))
                .andExpect(content().string(containsString(">Reopen<")));
    }

    @Test
    @DisplayName("TC-SM-37, TC-UC-12d, TC-SC-06, TC-Q-17 a linked original cannot be withdrawn or chained")
    void linkedOriginalRetainsItsDependents() throws Exception {
        var original = complaints.find(ORIGINAL, false).orElseThrow();
        jdbc.sql("UPDATE complaint SET status = 'ASSIGNED' WHERE id = :id").param("id", original.id()).update();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM complaint WHERE duplicate_of_id = :id")
                .param("id", original.id()).query(Integer.class).single()).isEqualTo(1);
        String ownerActions = detail(ORIGINAL).replace("/complaints", "/citizen/complaints");
        var owner = appUser(identities, OWNER);
        mvc.perform(get(detail(ORIGINAL)).with(owner)).andExpect(content().string(containsString("Other citizens are following this complaint")));
        mvc.perform(post(ownerActions + "/withdraw").with(owner).with(csrf())
                        .param("version", Integer.toString(original.version())).param("remark", NOTE))
                .andExpect(redirectedUrl(detail(ORIGINAL)))
                .andExpect(flash().attribute("flashError", containsString("Other citizens are following this complaint")));
        mvc.perform(post(duplicate("admin", ORIGINAL)).with(appUser(identities, ADMIN)).with(csrf())
                        .param("originalNo", SOURCE).param("version", Integer.toString(original.version())).param("remark", NOTE))
                .andExpect(redirectedUrl(detail(ORIGINAL))).andExpect(flash().attributeExists("flashError"));
        assertThat(complaints.find(ORIGINAL, false).orElseThrow().status()).isEqualTo(ComplaintStatus.ASSIGNED);
        assertThat(complaints.find(SEEDED_DUPLICATE, false).orElseThrow().originalNo()).isEqualTo(ORIGINAL);
    }

    @Test
    @DisplayName("TC-SC-08 another department's original is unavailable to the officer preview")
    void crossDepartmentPreviewKeepsSendBackAsAnOption() throws Exception {
        String source = "GRV-2026-000003";
        jdbc.sql("""
                UPDATE complaint SET status = 'ASSIGNED',
                    department_id = (SELECT id FROM department WHERE name = 'Sanitation') WHERE complaint_no = :no
                """).param("no", source).update();
        var officer = appUser(identities, "suresh.patil@example.com");
        mvc.perform(get(duplicate("officer", source)).with(officer).param("original", ORIGINAL))
                .andExpect(status().isOk()).andExpect(model().attribute("duplicateConfirm", false))
                .andExpect(model().attributeHasFieldErrorCode("form", "originalNo", "error.duplicate.otherDepartment"));
        mvc.perform(get(detail(source)).with(officer)).andExpect(content().string(containsString(">Send back<")));
    }

    @Test
    @DisplayName("TC-UT-17 demo seed is idempotent and includes the Appendix A duplicate and watcher")
    void demoLinksAndDateOffsetsRemainStableOnRestart() {
        var original = complaints.find(ORIGINAL, false).orElseThrow();
        assertThat(complaints.find(SEEDED_DUPLICATE, false).orElseThrow().originalNo()).isEqualTo(ORIGINAL);
        assertThat(activeRecipients(original.id())).containsExactly(LINKED);
        var now = LocalDateTime.now(testClock);
        assertThat(complaints.find(SOURCE, false).orElseThrow().createdAt()).isEqualTo(now.minusDays(15));
        assertThat(complaints.find("GRV-2026-000011", false).orElseThrow().resolvedAt()).isEqualTo(now.minusDays(8));
        assertThat(count("complaint")).isEqualTo(11);
        assertThat(jdbc.sql("SELECT last_number FROM complaint_sequence WHERE seq_year = 2026")
                .query(Integer.class).single()).isEqualTo(11);
        long historyBefore = count("complaint_event");
        long watchersBefore = count("complaint_watcher");
        users.seedUsers();
        demo.seed();
        assertThat(count("complaint")).isEqualTo(11);
        assertThat(count("complaint_event")).isEqualTo(historyBefore);
        assertThat(count("complaint_watcher")).isEqualTo(watchersBefore);
    }

    private List<String> latestRecipients(long complaint) {
        return jdbc.sql("""
                SELECT u.email FROM notification n JOIN app_user u ON u.id = n.recipient_id
                WHERE n.event_id = (SELECT MAX(id) FROM complaint_event WHERE complaint_id = :id)
                """).param("id", complaint).query(String.class).list();
    }

    private List<String> activeRecipients(long complaint) {
        return jdbc.sql("""
                SELECT u.email FROM complaint_watcher w JOIN app_user u ON u.id = w.user_id
                WHERE w.complaint_id = :id AND w.active = TRUE
                """).param("id", complaint).query(String.class).list();
    }

    private void watcher(long complaint, String email, boolean active) {
        jdbc.sql("""
                INSERT INTO complaint_watcher (complaint_id, user_id, active, since, updated_at)
                VALUES (:id, :user, :active, :old, :old)
                """).param("id", complaint).param("user", identities.loadUserByUsername(email).id()).param("active", active)
                .param("old", LocalDateTime.now(testClock).minusDays(3)).update();
    }

    private long count(String table) { return jdbc.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single(); }
    private static String duplicate(String prefix, String no) { return "/" + prefix + "/complaints/" + no + "/duplicate"; }
    private static String watch(String no, String action) { return "/citizen/complaints/" + no + "/" + action; }
    private static String detail(String no) { return "/complaints/" + no; }
}
