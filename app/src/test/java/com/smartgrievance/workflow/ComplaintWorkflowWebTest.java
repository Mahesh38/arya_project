package com.smartgrievance.workflow;

import com.smartgrievance.complaint.ComplaintRepository;
import com.smartgrievance.complaint.ComplaintStatus;
import com.smartgrievance.masterdata.LookupOption;
import com.smartgrievance.seed.ComplaintDemoData;
import com.smartgrievance.seed.DemoData;
import com.smartgrievance.testsupport.Html;
import com.smartgrievance.testsupport.LogCapture;
import com.smartgrievance.testsupport.WebTestBase;
import com.smartgrievance.user.AccountBootstrap;
import com.smartgrievance.user.AppUserDetailsService;
import com.smartgrievance.workflow.TransitionRules.Action;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import static com.smartgrievance.complaint.ComplaintStatus.*;
import static com.smartgrievance.testsupport.Logins.appUser;
import static com.smartgrievance.workflow.TransitionRules.Action.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ComplaintWorkflowWebTest extends WebTestBase {
    private static final String ADMIN = "admin";
    private static final String MAHESH = "mahesh";
    private static final String ANITA = "anita.deshmukh";
    private static final String VIKRAM = "vikram.jadhav";
    private static final String SURESH = "suresh.patil";
    private static final String REMARK = "This is a test update on the work.";
    @RegisterExtension final LogCapture logs = new LogCapture();
    @Autowired AccountBootstrap bootstrap;
    @Autowired DemoData users;
    @Autowired ComplaintDemoData demo;
    @Autowired AppUserDetailsService identities;
    @Autowired ComplaintRepository complaints;
    @Autowired JdbcClient jdbc;
    @Autowired AutoCloseJob autoClose;

    @BeforeEach
    void seed() {
        bootstrap.ensureAdmin(properties.admin(), false);
        users.seedUsers();
        demo.seed();
    }

    record Case(String ids, String role, String actor, int number, String segment, Action action, ComplaintStatus to,
                String flash, List<String> recipients) {
        String no() { return "GRV-2026-%06d".formatted(number); }
        String formPath() { return "/" + role + "/complaints/" + no() + "/" + segment; }
        String detail() { return "/complaints/" + no(); }
        @Override public String toString() { return ids + " " + role + " " + segment; }
    }

    static Stream<Case> routes() {
        return Stream.of(
                new Case("TC-RT-40, TC-RT-41, TC-SM-11, TC-UC-15a, TC-UC-15b", "citizen", MAHESH, 4, "close", CLOSE, CLOSED,
                        "Thank you. The complaint is closed.", List.of("amit.pawar")),
                new Case("TC-RT-42, TC-RT-43, TC-SM-12, TC-UC-15c", "citizen", MAHESH, 4, "reopen", REOPEN, REOPENED,
                        "Complaint reopened and sent back to the department.", List.of(ADMIN, "amit.pawar")),
                new Case("TC-RT-44, TC-RT-45, TC-SM-05, TC-UC-31a, TC-UC-31b", "officer", SURESH, 7, "start", START_WORK, IN_PROGRESS,
                        "Work started.", List.of("jayesh")),
                new Case("TC-RT-46, TC-RT-47, TC-SM-07, TC-UC-32a", "officer", ANITA, 1, "remark", ADD_REMARK, IN_PROGRESS,
                        "Update added.", List.of(MAHESH, "jayesh")),
                new Case("TC-RT-48, TC-RT-49, TC-SM-09, TC-UC-33a, TC-UC-33b", "officer", ANITA, 1, "resolve", RESOLVE, RESOLVED,
                        "Complaint resolved. The citizen has until 30-Nov-2026 to review it.", List.of(MAHESH, "jayesh")),
                new Case("TC-RT-50, TC-RT-51, TC-SM-14, TC-UC-34a", "officer", VIKRAM, 5, "send-back", SEND_BACK, UNDER_REVIEW,
                        "GRV-2026-000005 sent back to the admin.", List.of(ADMIN)),
                new Case("TC-RT-52, TC-RT-53, TC-SM-16, TC-UC-22b", "officer", VIKRAM, 5, "reject", REJECT, REJECTED,
                        "Complaint rejected.", List.of("jayesh")),
                new Case("TC-RT-56, TC-SM-02, TC-UC-20a", "admin", ADMIN, 3, "review", START_REVIEW, UNDER_REVIEW,
                        "Review started.", List.of("sneha")),
                new Case("TC-RT-57, TC-RT-58, TC-SM-03, TC-UC-21a", "admin", ADMIN, 8, "assign", ASSIGN, ASSIGNED,
                        "Assigned to Water Supply.", List.of("sneha", "jayesh", ANITA, "rohan.kulkarni")),
                new Case("TC-RT-59, TC-RT-60, TC-SM-04, TC-UC-21c", "admin", ADMIN, 5, "reassign", REASSIGN, ASSIGNED,
                        "Reassigned to Drainage.", List.of("jayesh", "pooja.shinde")),
                new Case("TC-RT-61, TC-RT-62, TC-SM-15, TC-UC-22a", "admin", ADMIN, 3, "reject", REJECT, REJECTED,
                        "Complaint rejected.", List.of("sneha")),
                new Case("TC-RT-65, TC-RT-66, TC-SM-06", "admin", ADMIN, 5, "start", START_WORK, IN_PROGRESS,
                        "Work started.", List.of("jayesh")),
                new Case("TC-RT-67, TC-RT-68, TC-SM-08", "admin", ADMIN, 1, "remark", ADD_REMARK, IN_PROGRESS,
                        "Update added.", List.of(MAHESH, "jayesh")),
                new Case("TC-RT-69, TC-RT-70, TC-SM-10", "admin", ADMIN, 1, "resolve", RESOLVE, RESOLVED,
                        "Complaint resolved. The citizen has until 30-Nov-2026 to review it.", List.of(MAHESH, "jayesh")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("routes")
    void exactRoutesAndRecipients(Case example) throws Exception {
        var before = complaint(example.no());
        if (before.number().endsWith("000001") || example.action == ASSIGN) watcher(before.id(), "jayesh");
        if (example.action != START_REVIEW) {
            var page = mvc.perform(get(example.formPath()).with(appUser(identities, email(example.actor))))
                    .andExpect(status().isOk()).andExpect(view().name("complaint/action-form"))
                    .andExpect(model().attribute("action", example.action)).andExpect(model().attribute("postUrl", example.formPath()))
                    .andExpect(model().attribute("cancelUrl", example.detail())).andReturn();
            var attributes = page.getModelAndView().getModel();
            assertThat((VersionForm) attributes.get("form")).extracting(VersionForm::getVersion).isEqualTo(before.version());
            if (example.action == REOPEN) assertThat(attributes).containsEntry("reopensLeft", 2);
            if (example.action == RESOLVE) assertThat(attributes).containsEntry("maxFiles", 3);
            if (example.action == REASSIGN) {
                assertThat((List<?>) attributes.get("departments")).noneMatch(option ->
                        ((LookupOption) option).id() == before.departmentId());
            }
        }
        var request = example.action == RESOLVE ? multipart(example.formPath()).file(photo()) : post(example.formPath());
        if (example.action == ASSIGN) request.param("departmentId", department("Water Supply"));
        if (example.action == REASSIGN) request.param("departmentId", department("Drainage"));
        mvc.perform(work(request, example.actor, before.version(), REMARK))
                .andExpect(redirectedUrl(example.action == SEND_BACK ? "/officer/queue" : example.detail()))
                .andExpect(flash().attribute("flashSuccess", example.flash));
        var after = complaint(example.no());
        assertThat(after.status()).isEqualTo(example.to);
        assertThat(after.version()).isEqualTo(before.version() + 1);
        var event = jdbc.sql("""
                SELECT event_type, from_status, to_status, actor_id, department_id, remark FROM complaint_event
                WHERE complaint_id = :id ORDER BY id DESC LIMIT 1
                """).param("id", before.id()).query().singleRow();
        String eventType = example.action == ADD_REMARK ? "REMARK" : example.action == REASSIGN ? "REASSIGNED" : "STATUS_CHANGED";
        assertThat(event).containsEntry("event_type", eventType).containsEntry("actor_id", identities.loadUserByUsername(email(example.actor)).id())
                .containsEntry("from_status", example.action == ADD_REMARK ? null : before.status().name())
                .containsEntry("to_status", example.action == ADD_REMARK ? null : example.to.name())
                .containsEntry("remark", example.action == START_REVIEW ? null : REMARK);
        assertThat(recipients(before.id())).containsExactlyInAnyOrderElementsOf(example.recipients.stream().map(this::email).toList());
        if (example.action == SEND_BACK) {
            assertThat(after.departmentId()).isNull();
            assertThat(event).containsEntry("department_id", before.departmentId());
            mvc.perform(get(example.detail()).with(appUser(identities, email(example.actor)))).andExpect(status().isNotFound());
        }
        if (example.action == REOPEN) {
            assertThat(after.resolvedAt()).isNull();
            assertThat(after.reopenCount()).isEqualTo(before.reopenCount() + 1);
        }
        if (example.action == RESOLVE) {
            assertThat(after.resolvedAt()).isEqualTo(LocalDateTime.now(testClock));
            assertThat(jdbc.sql("SELECT COUNT(*) FROM attachment WHERE complaint_id = :id AND purpose = 'RESOLUTION'")
                    .param("id", after.id()).query(Integer.class).single()).isEqualTo(1);
        }
        if (example.action != ADD_REMARK) {
            assertThat(logs.entries()).anySatisfy(log -> assertThat(log.getFormattedMessage())
                    .contains("COMPLAINT_TRANSITION complaintNo=" + example.no(), "from=" + before.status(),
                            "to=" + after.status(), "actorId=" + identities.loadUserByUsername(email(example.actor)).id()));
        }
    }

    @Test
    @DisplayName("TC-SM-39, TC-SM-40, TC-VE-08, TC-UC-21b, TC-UC-21d department choices distinguish inactive and same")
    void invalidDepartmentChoicesDoNotChangeState() throws Exception {
        var assign = complaint(no(8));
        var reassign = complaint(no(5));
        String drainage = department("Drainage");
        jdbc.sql("UPDATE department SET active = FALSE WHERE id = :id").param("id", drainage).update();
        mvc.perform(work(post(admin(8, "assign")), ADMIN, assign.version(), null).param("departmentId", drainage))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrorCode("form", "departmentId", "validation.departmentId.inactive"));
        mvc.perform(work(post(admin(5, "reassign")), ADMIN, reassign.version(), REMARK).param("departmentId", drainage))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrorCode("form", "departmentId", "validation.departmentId.inactive"));
        mvc.perform(work(post(admin(5, "reassign")), ADMIN, reassign.version(), REMARK).param("departmentId", reassign.departmentId().toString()))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrorCode("form", "departmentId", "validation.departmentId.same"));
        assertThat(complaint(no(8)).version()).isEqualTo(assign.version());
        assertThat(complaint(no(5)).version()).isEqualTo(reassign.version());
    }

    @Test
    @DisplayName("TC-SM-45, TC-SEC-22 reassignment immediately changes officer scope")
    void departmentScopeChangesAfterReassignment() throws Exception {
        var before = complaint(no(5));
        mvc.perform(work(post(admin(5, "reassign")), ADMIN, before.version(), REMARK).param("departmentId", department("Drainage")))
                .andExpect(redirectedUrl("/complaints/" + no(5)));
        String path = "/officer/complaints/" + no(5) + "/start";
        mvc.perform(get("/complaints/" + no(5)).with(appUser(identities, email(VIKRAM)))).andExpect(status().isNotFound());
        mvc.perform(get(path).with(appUser(identities, email(VIKRAM)))).andExpect(status().isNotFound());
        mvc.perform(work(post(path), VIKRAM, before.version() + 1, REMARK)).andExpect(status().isNotFound());
        mvc.perform(get(path).with(appUser(identities, email("pooja.shinde")))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-SM-34, TC-SM-35, TC-SM-36, TC-VE-19 review deadline is inclusive and reopen limits leave close available")
    void deadlineAndReopenLimitAreEnforced() throws Exception {
        var resolved = complaint(no(4));
        String path = "/citizen/complaints/" + no(4) + "/";
        testClock.set(resolved.resolvedAt().plusDays(7));
        mvc.perform(get(path + "close").with(appUser(identities, email(MAHESH)))).andExpect(status().isOk());
        jdbc.sql("UPDATE complaint SET reopen_count = 2 WHERE id = :id").param("id", resolved.id()).update();
        mvc.perform(get(path + "reopen").with(appUser(identities, email(MAHESH))))
                .andExpect(redirectedUrl("/complaints/" + no(4)))
                .andExpect(flash().attribute("flashError", "Reopen limit reached; please contact the department."));
        autoClose.run();
        assertThat(complaint(no(4)).status()).isEqualTo(RESOLVED);
        testClock.advance(Duration.ofSeconds(1));
        mvc.perform(get(path + "close").with(appUser(identities, email(MAHESH))))
                .andExpect(redirectedUrl("/complaints/" + no(4)))
                .andExpect(flash().attribute("flashError", "The review period ended on 28-Nov-2026; the complaint will be closed automatically."));
        autoClose.run();
        assertThat(complaint(no(4)).status()).isEqualTo(CLOSED);
    }

    @Test
    @DisplayName("TC-SM-13, TC-Q-18, TC-Q-21, TC-UC-70a, TC-SC-09 auto-close is optimistic and records a single system event")
    void autoCloseRemovesOnlyExpiredRows() {
        var old = complaint(no(11));
        var current = complaint(no(4));
        assertThat(complaints.findExpiredResolved(LocalDateTime.now(testClock).minusDays(7), 500, null))
                .extracting(ComplaintRepository.Expired::number).containsExactly(old.number());
        autoClose.run();
        autoClose.run();
        var after = complaint(old.number());
        assertThat(after.status()).isEqualTo(CLOSED);
        assertThat(after.version()).isEqualTo(old.version() + 1);
        assertThat(complaint(current.number()).status()).isEqualTo(RESOLVED);
        var event = jdbc.sql("SELECT actor_id, remark FROM complaint_event WHERE complaint_id = :id AND event_type = 'AUTO_CLOSED'")
                .param("id", old.id()).query().singleRow();
        assertThat(event).containsEntry("actor_id", null).containsEntry("remark", "Auto-closed: no response within 7 days");
        assertThat(recipients(old.id())).containsExactlyInAnyOrder(email(MAHESH), email("amit.pawar"));
        assertThat(logs.entries()).anySatisfy(log -> assertThat(log.getFormattedMessage()).contains("Auto-close job closed 1 complaints"));
    }

    @Test
    @DisplayName("TC-BR-11, TC-SC-04, TC-SM-42 a reopen resets the window and a stale form cannot change the row")
    void eachResolutionStartsFreshWindow() throws Exception {
        var resolved = complaint(no(4));
        mvc.perform(work(post("/citizen/complaints/" + no(4) + "/reopen"), MAHESH, resolved.version(), REMARK))
                .andExpect(redirectedUrl("/complaints/" + no(4)));
        testClock.advance(Duration.ofDays(3));
        mvc.perform(work(post(admin(4, "start")), ADMIN, resolved.version(), REMARK))
                .andExpect(redirectedUrl("/complaints/" + no(4)))
                .andExpect(flash().attribute("flashError", "This complaint was updated by someone else, please refresh."));
        assertThat(complaint(no(4)).status()).isEqualTo(REOPENED);
        mvc.perform(work(post(admin(4, "start")), ADMIN, resolved.version() + 1, REMARK)).andExpect(redirectedUrl("/complaints/" + no(4)));
        mvc.perform(work(multipart(admin(4, "resolve")), ADMIN, resolved.version() + 2, REMARK))
                .andExpect(redirectedUrl("/complaints/" + no(4)))
                .andExpect(flash().attribute("flashSuccess", "Complaint resolved. The citizen has until 03-Dec-2026 to review it."));
        assertThat(complaint(no(4)).resolvedAt()).isEqualTo(LocalDateTime.of(2026, 11, 26, 10, 0));
    }

    @Test
    @DisplayName("TC-SC-01, TC-NF-13 one submission and four logged workflow transitions notify the owner five times")
    void citizenComplaintFlowsFromSubmissionThroughResolution() throws Exception {
        mvc.perform(multipart("/citizen/complaints").with(appUser(identities, email(MAHESH))).with(csrf())
                        .param("title", "Water leaking near the school")
                        .param("description", "Water has been leaking for several days near the school.")
                        .param("address", "School playground entrance, Kothrud")
                        .param("categoryId", jdbc.sql("SELECT id FROM category WHERE name = 'Water Leakage'").query(String.class).single())
                        .param("areaId", jdbc.sql("SELECT id FROM area WHERE name = 'Kothrud'").query(String.class).single()))
                .andExpect(redirectedUrl("/citizen/complaints/" + no(12) + "/submitted"));
        var first = complaint(no(12));
        mvc.perform(work(post(admin(12, "review")), ADMIN, first.version(), null)).andExpect(redirectedUrl("/complaints/" + no(12)));
        mvc.perform(work(post(admin(12, "assign")), ADMIN, first.version() + 1, null).param("departmentId", department("Water Supply")))
                .andExpect(redirectedUrl("/complaints/" + no(12)));
        mvc.perform(work(post("/officer/complaints/" + no(12) + "/start"), ANITA, first.version() + 2, REMARK))
                .andExpect(redirectedUrl("/complaints/" + no(12)));
        mvc.perform(work(multipart("/officer/complaints/" + no(12) + "/resolve"), ANITA, first.version() + 3, REMARK))
                .andExpect(redirectedUrl("/complaints/" + no(12)));
        assertThat(jdbc.sql("SELECT to_status FROM complaint_event WHERE complaint_id = :id ORDER BY id")
                .param("id", first.id()).query(String.class).list())
                .containsExactly("SUBMITTED", "UNDER_REVIEW", "ASSIGNED", "IN_PROGRESS", "RESOLVED");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM notification WHERE complaint_id = :id AND recipient_id = :owner")
                .param("id", first.id()).param("owner", first.ownerId()).query(Integer.class).single()).isEqualTo(5);
        assertThat(logs.entries()).filteredOn(log -> log.getFormattedMessage().startsWith("COMPLAINT_TRANSITION complaintNo=" + no(12)))
                .hasSize(5).allSatisfy(log -> {
                    assertThat(log.getLevel()).isEqualTo(ch.qos.logback.classic.Level.INFO);
                    assertThat(log.getFormattedMessage()).contains("from=", "to=", "actorId=");
                });
    }

    @Test
    @DisplayName("TC-UC-22c, TC-SC-07, TC-SEC-50 reject respects scope and status, and officer detail omits contact data")
    void rejectionIsScopedAndOfficerDetailDoesNotExposeContactData() throws Exception {
        mvc.perform(get("/officer/complaints/" + no(8) + "/reject").with(appUser(identities, email(VIKRAM))))
                .andExpect(status().isNotFound());
        var resolved = complaint(no(4));
        mvc.perform(work(post(admin(4, "reject")), ADMIN, resolved.version(), REMARK))
                .andExpect(redirectedUrl("/complaints/" + no(4)))
                .andExpect(flash().attribute("flashError", "This action is not allowed when the complaint is Resolved."));
        var hidden = mvc.perform(get("/complaints/" + no(1)).with(appUser(identities, email(SURESH))))
                .andExpect(status().isNotFound()).andReturn();
        var unknown = mvc.perform(get("/complaints/" + no(999999)).with(appUser(identities, email(SURESH))))
                .andExpect(status().isNotFound()).andReturn();
        assertThat(Html.of(hidden.getResponse().getContentAsString()).text("main"))
                .isEqualTo(Html.of(unknown.getResponse().getContentAsString()).text("main"));
        var officer = mvc.perform(get("/complaints/" + no(1)).with(appUser(identities, email(ANITA))))
                .andExpect(status().isOk()).andReturn();
        var owner = identities.loadUserByUsername(email(MAHESH));
        assertThat(officer.getResponse().getContentAsString()).contains(owner.fullName()).doesNotContain(email(MAHESH));
        var contact = jdbc.sql("SELECT mobile FROM app_user WHERE id = :id").param("id", owner.id()).query(String.class).single();
        assertThat(officer.getResponse().getContentAsString()).doesNotContain(contact);
    }

    @Test
    @DisplayName("TC-UC-15d, TC-SM-41 expired reviews and linked viewers cannot take owner actions")
    void reviewWindowRefusalMatchesDetailNotice() throws Exception {
        var current = complaint(no(4));
        watcher(current.id(), "jayesh");
        String detail = "/complaints/" + no(4);
        String close = "/citizen/complaints/" + no(4) + "/close";
        mvc.perform(work(post(close), "jayesh", current.version(), null)).andExpect(redirectedUrl(detail))
                .andExpect(flash().attribute("flashError", "Only the person who filed this complaint can do this."));
        testClock.advance(Duration.ofDays(6));
        String message = "The review period ended on 28-Nov-2026; the complaint will be closed automatically.";
        var expired = mvc.perform(get(detail).with(appUser(identities, email(MAHESH)))).andExpect(status().isOk()).andReturn();
        assertThat(expired.getResponse().getContentAsString()).contains(message).doesNotContain("href=\"" + close + "\"");
        mvc.perform(work(post(close), MAHESH, current.version(), null)).andExpect(redirectedUrl(detail))
                .andExpect(flash().attribute("flashError", message));
        assertThat(complaint(no(4)).version()).isEqualTo(current.version());
    }

    static Stream<Arguments> remarkRoutes() {
        return Stream.of(
                Arguments.of(3, "citizen", "withdraw", "sneha", true, false, null),
                Arguments.of(4, "citizen", "close", MAHESH, false, false, null),
                Arguments.of(4, "citizen", "reopen", MAHESH, true, false, null),
                Arguments.of(7, "officer", "start", SURESH, true, false, null),
                Arguments.of(1, "officer", "resolve", ANITA, true, true, null),
                Arguments.of(5, "officer", "reject", VIKRAM, true, false, null),
                Arguments.of(8, "admin", "assign", ADMIN, false, false, "Water Supply"),
                Arguments.of(5, "admin", "reassign", ADMIN, true, false, "Drainage"),
                Arguments.of(3, "admin", "reject", ADMIN, true, false, null));
    }

    @ParameterizedTest(name = "TC-VE-04 {1}/{2}")
    @MethodSource("remarkRoutes")
    void remarkValidationPreservesNewlinesAndEnforcesTheActionBounds(int number, String role, String segment,
            String who, boolean required, boolean multipart, String assignment) throws Exception {
        var before = complaint(no(number));
        String path = "/" + role + "/complaints/" + before.number() + "/" + segment;
        List<String> invalid = required ? List.of(" ", "x".repeat(9), "x".repeat(501)) : List.of("x".repeat(501));
        for (String remark : invalid) {
            var form = work(multipart ? multipart(path) : post(path), who, before.version(), remark);
            if (assignment != null) form.param("departmentId", department(assignment));
            mvc.perform(form).andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "remark"));
        }
        assertThat(complaint(no(number)).version()).isEqualTo(before.version());
        String first = "x".repeat(249);
        String last = "x".repeat(250);
        var acceptable = work(multipart ? multipart(path) : post(path), who, before.version(), first + "\r\n" + last);
        if (assignment != null) acceptable.param("departmentId", department(assignment));
        mvc.perform(acceptable).andExpect(redirectedUrl("/complaints/" + before.number()));
        assertThat(jdbc.sql("SELECT remark FROM complaint_event WHERE complaint_id = :id ORDER BY id DESC LIMIT 1")
                .param("id", before.id()).query(String.class).single()).isEqualTo(first + "\n" + last);
    }

    private AbstractMockHttpServletRequestBuilder<?> work(AbstractMockHttpServletRequestBuilder<?> request, String actor, int version, String remark) {
        request.with(appUser(identities, email(actor))).with(csrf()).param("version", Integer.toString(version));
        if (remark != null) request.param("remark", remark);
        return request;
    }

    private void watcher(long complaint, String who) {
        jdbc.sql("""
                INSERT INTO complaint_watcher (complaint_id, user_id, active, since, updated_at)
                VALUES (:complaint, :user, TRUE, :now, :now)
                """).param("complaint", complaint).param("user", identities.loadUserByUsername(email(who)).id())
                .param("now", LocalDateTime.now(testClock)).update();
    }

    private List<String> recipients(long complaintId) {
        return jdbc.sql("""
                SELECT u.email FROM notification n JOIN app_user u ON u.id = n.recipient_id
                WHERE n.event_id = (SELECT MAX(id) FROM complaint_event WHERE complaint_id = :id)
                """).param("id", complaintId).query(String.class).list();
    }

    private ComplaintRepository.Complaint complaint(String no) { return complaints.find(no, false).orElseThrow(); }
    private String department(String name) {
        return jdbc.sql("SELECT id FROM department WHERE name = :name").param("name", name).query(String.class).single();
    }
    private static String no(int number) { return "GRV-2026-%06d".formatted(number); }
    private static String admin(int number, String action) { return "/admin/complaints/" + no(number) + "/" + action; }
    private String email(String name) { return name.equals(ADMIN) ? properties.admin().email() : name + "@example.com"; }
    private static MockMultipartFile photo() throws Exception {
        return new MockMultipartFile("files", "fixed.png", "image/png", new ClassPathResource("files/photo.png").getContentAsByteArray());
    }
}
