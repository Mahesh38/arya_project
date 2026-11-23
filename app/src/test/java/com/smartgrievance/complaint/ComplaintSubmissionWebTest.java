package com.smartgrievance.complaint;

import com.smartgrievance.seed.ComplaintDemoData;
import com.smartgrievance.seed.DemoData;
import com.smartgrievance.testsupport.Html;
import com.smartgrievance.testsupport.Logins;
import com.smartgrievance.testsupport.TestDatabase;
import com.smartgrievance.testsupport.WebTestBase;
import com.smartgrievance.user.AccountBootstrap;
import com.smartgrievance.user.AppUserDetailsService;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ComplaintSubmissionWebTest extends WebTestBase {
    private static final String BASE = "/citizen/complaints";
    private static final String FIRST = "GRV-2026-000001";
    private static final String TITLE = "Water mains leaking near the park";
    private static final String DESCRIPTION = "Water has been leaking from the mains since Wednesday.";
    private static final String ADDRESS = "Karve Road, Kothrud";
    @Autowired JdbcClient jdbc;
    @Autowired AccountBootstrap bootstrap;
    @Autowired DemoData users;
    @Autowired ComplaintDemoData demo;
    @Autowired AppUserDetailsService identities;
    @Autowired ComplaintService submission;
    @Autowired ComplaintRepository repository;
    @Autowired ComplaintNumberGenerator numbers;
    @Autowired TransactionTemplate transaction;

    @BeforeEach
    void accounts() {
        bootstrap.ensureAdmin(properties.admin(), false);
        users.seedUsers();
    }

    @Test
    @DisplayName("TC-RT-22, TC-RT-23, TC-RT-24, TC-BR-34, TC-UC-10a verified submission and owner confirmation")
    void createAndConfirmationRequireVerifiedCitizenAndOwnerRespectively() throws Exception {
        demo.seed();
        jdbc.sql("UPDATE category SET active = FALSE WHERE name = 'Garbage'").update();
        mvc.perform(get(BASE + "/new").with(Logins.appUser(identities, "mahesh@example.com")))
                .andExpect(status().isOk()).andExpect(view().name("citizen/complaint-form"))
                .andExpect(model().attribute("mode", "CREATE")).andExpect(model().attribute("maxFiles", 5))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(">Garbage</option>"))));
        mvc.perform(get(BASE + "/new").with(Logins.appUser(identities, "rahul@example.com")))
                .andExpect(redirectedUrl("/verify-mobile"));
        mvc.perform(complaint(post(BASE), "rahul", TITLE)).andExpect(redirectedUrl("/verify-mobile"));
        jdbc.sql("UPDATE category SET active = TRUE WHERE name = 'Garbage'").update();
        var photo = photo();
        var number = "GRV-2026-000012";
        mvc.perform(complaint(multipart(BASE).file(photo), "mahesh", TITLE)
                        .param("ownerId", "1").param("status", "RESOLVED").param("version", "10"))
                .andExpect(redirectedUrl(BASE + "/" + number + "/submitted"));
        mvc.perform(get(BASE + "/" + number + "/submitted").with(Logins.appUser(identities, "mahesh@example.com")))
                .andExpect(status().isOk()).andExpect(view().name("citizen/submitted"))
                .andExpect(content().string(containsString(number)))
                .andExpect(content().string(containsString("View complaint")));
        mvc.perform(get(BASE + "/" + FIRST + "/submitted").with(Logins.appUser(identities, "jayesh@example.com")))
                .andExpect(status().isNotFound());
        assertThat(jdbc.sql("SELECT status, version FROM complaint WHERE complaint_no = :no").param("no", number)
                .query().singleRow()).containsEntry("status", "SUBMITTED").containsEntry("version", 0);
        assertThat(emailRecipients(number, "CREATED")).containsExactlyInAnyOrder("mahesh@example.com", properties.admin().email());
        assertThat(count("attachment")).isEqualTo(3);
    }

    @Test
    @DisplayName("TC-VE-03, TC-UC-10b create validation preserves the database")
    void invalidFieldsAndInactiveLookupLeaveNoRows() throws Exception {
        mvc.perform(post(BASE).with(Logins.appUser(identities, "mahesh@example.com")).with(csrf())
                        .param("categoryId", lookup("category", "Garbage")).param("title", "abcd")
                        .param("description", DESCRIPTION).param("address", ADDRESS))
                .andExpect(status().isOk()).andExpect(view().name("citizen/complaint-form"))
                .andExpect(model().attributeHasFieldErrors("form", "title", "areaId"))
                .andExpect(content().string(containsString(DESCRIPTION)));
        jdbc.sql("UPDATE category SET active = FALSE WHERE name = 'Garbage'").update();
        jdbc.sql("UPDATE area SET active = FALSE WHERE name = 'Kothrud'").update();
        mvc.perform(complaint(post(BASE), "mahesh", TITLE))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("form", "categoryId", "validation.categoryId.inactive"))
                .andExpect(model().attributeHasFieldErrorCode("form", "areaId", "validation.areaId.inactive"));
        assertThat(count("complaint")).isZero();
        assertThat(count("complaint_sequence")).isZero();
    }

    @Test
    @DisplayName("TC-BR-02, TC-UC-10d storage failure rolls back the sequence")
    void storageFailureRollsBackNumberAndReturnsReference() throws Exception {
        var year = properties.upload().dir().resolve("2026");
        Files.createDirectories(year.getParent());
        Files.createFile(year);
        mvc.perform(complaint(multipart(BASE).file(photo()), "mahesh", TITLE))
                .andExpect(status().isOk()).andExpect(view().name("citizen/complaint-form"))
                .andExpect(content().string(org.hamcrest.Matchers.matchesPattern("(?s).*ERR-[A-Z0-9]{4}-[A-Z0-9]{4}.*")));
        assertThat(count("complaint")).isZero();
        assertThat(count("complaint_sequence")).isZero();
        Files.delete(year);
        mvc.perform(complaint(multipart(BASE).file(photo()), "mahesh", TITLE))
                .andExpect(redirectedUrl(BASE + "/" + FIRST + "/submitted"));
    }

    @Test
    @DisplayName("TC-BR-08, TC-VE-17 notification constraint failure rolls back rows and files")
    void notificationIntegrityFailureRollsBackDatabaseAndRemovesWrittenFiles() throws Exception {
        var form = new ComplaintForm();
        form.setCategoryId(Long.parseLong(lookup("category", "Garbage")));
        form.setAreaId(Long.parseLong(lookup("area", "Kothrud")));
        form.setTitle(TITLE);
        form.setDescription(DESCRIPTION);
        form.setAddress(ADDRESS);
        var actor = identities.loadUserByUsername("mahesh@example.com");
        var photo = photo();
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            submission.submit(form, List.of(photo, photo), actor);
            assertThat(count("attachment")).isEqualTo(2);
            assertThat(uploadedFileCount()).isEqualTo(2);
            jdbc.sql("""
                    INSERT INTO notification (recipient_id, complaint_id, event_id, message, created_at)
                    SELECT recipient_id, complaint_id, event_id, message, created_at FROM notification
                    """).update();
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("complaint")).isZero();
        assertThat(count("attachment")).isZero();
        assertThat(count("complaint_event")).isZero();
        assertThat(count("complaint_sequence")).isZero();
        assertThat(count("notification")).isZero();
        assertThat(uploadedFileCount()).isZero();
    }

    @Test
    @DisplayName("TC-Q-19, TC-Q-20 optimistic writes enforce status and version")
    void optimisticStatusUpdateChecksBothStatusAndVersion() {
        jdbc.sql("DELETE FROM app_user").update();
        jdbc.sql("ALTER TABLE app_user ALTER COLUMN id RESTART WITH 1").update();
        TestDatabase.load(dataSource, "sql/q-fixture.sql");
        var prior = repository.find(FIRST, false).orElseThrow();
        var now = LocalDateTime.now(testClock);
        assertThat(repository.updateStatus(prior.id(), prior.version(), ComplaintStatus.ASSIGNED, ComplaintStatus.RESOLVED, now))
                .isFalse();
        assertThat(repository.updateStatus(prior.id(), prior.version(), ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLVED, now))
                .isTrue();
        assertThat(repository.updateStatus(prior.id(), prior.version(), ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLVED, now))
                .isFalse();
        var current = repository.find(FIRST, false).orElseThrow();
        assertThat(current.status()).isEqualTo(ComplaintStatus.RESOLVED);
        assertThat(current.version()).isEqualTo(prior.version() + 1);
        assertThat(current.resolvedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("TC-BR-01, TC-BR-36, TC-SEC-42 IST numbering and rolling submission limit")
    void yearlySequenceUsesIstAndRollingLimitIsAppliedBeforeNumberAllocation() throws Exception {
        testClock.set(LocalDateTime.of(2026, 12, 31, 23, 59));
        jdbc.sql("INSERT INTO complaint_sequence (seq_year, last_number) VALUES (2026, 41)").update();
        mvc.perform(complaint(post(BASE), "mahesh", TITLE))
                .andExpect(redirectedUrl(BASE + "/GRV-2026-000042/submitted"));
        testClock.advance(Duration.ofMinutes(1));
        for (int i = 1; i <= 9; i++) {
            mvc.perform(complaint(post(BASE), "mahesh", TITLE))
                    .andExpect(redirectedUrl(BASE + "/GRV-2027-%06d/submitted".formatted(i)));
        }
        mvc.perform(complaint(post(BASE), "mahesh", TITLE)).andExpect(status().isOk())
                .andExpect(model().attributeHasErrors("form"));
        assertThat(count("complaint")).isEqualTo(10);
        assertThat(jdbc.sql("SELECT MAX(created_at) FROM complaint").query(LocalDateTime.class).single())
                .isEqualTo(LocalDateTime.of(2027, 1, 1, 0, 0));
        testClock.advance(Duration.ofHours(24).plusSeconds(1));
        mvc.perform(complaint(post(BASE), "mahesh", TITLE))
                .andExpect(redirectedUrl(BASE + "/GRV-2027-000010/submitted"));
    }

    @Test
    @DisplayName("TC-RT-25, TC-RT-26, TC-BR-03, TC-BR-04, TC-BR-10, TC-UC-11a, TC-UC-11d edit choices, no-op and optimistic lock")
    void editRetainsInactiveCurrentChoiceTracksRevisionsAndRejectsStaleUpdates() throws Exception {
        mvc.perform(complaint(multipart(BASE).file(photo()), "mahesh", TITLE)).andExpect(status().isFound());
        jdbc.sql("UPDATE category SET active = FALSE WHERE name IN ('Garbage', 'Streetlight')").update();
        jdbc.sql("UPDATE area SET active = FALSE WHERE name IN ('Kothrud', 'Aundh')").update();
        jdbc.sql("UPDATE complaint SET status = 'UNDER_REVIEW'").update();
        var edit = BASE + "/" + FIRST + "/edit";
        mvc.perform(get(edit).with(Logins.appUser(identities, "mahesh@example.com")))
                .andExpect(status().isOk()).andExpect(model().attribute("mode", "EDIT"))
                .andExpect(content().string(containsString(TITLE)))
                .andExpect(content().string(containsString("photo.png")))
                .andExpect(content().string(containsString("Garbage (no longer offered)")))
                .andExpect(content().string(containsString("Kothrud (no longer offered)")));
        mvc.perform(post(edit).with(Logins.appUser(identities, "mahesh@example.com")).with(csrf())
                        .param("categoryId", lookup("category", "Streetlight")).param("areaId", lookup("area", "Aundh"))
                        .param("title", TITLE).param("description", DESCRIPTION).param("address", ADDRESS).param("version", "0"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("form", "categoryId", "validation.categoryId.inactive"))
                .andExpect(model().attributeHasFieldErrorCode("form", "areaId", "validation.areaId.inactive"));
        mvc.perform(complaint(post(edit), "mahesh", TITLE).param("version", "0")
                        .param("removeAttachmentIds", "999999"))
                .andExpect(redirectedUrl("/complaints/" + FIRST));
        assertThat(count("complaint_event")).isEqualTo(1);
        assertThat(count("complaint_revision")).isZero();
        assertThat(count("notification")).isEqualTo(2);
        mvc.perform(complaint(post(edit), "mahesh", "Updated report about the water main")
                        .param("version", "0").param("changeReason", "Corrected the title")
                        .param("ownerId", "1").param("status", "CLOSED").param("complaintNo", "forged"))
                .andExpect(redirectedUrl("/complaints/" + FIRST));
        assertThat(jdbc.sql("SELECT complaint_no, status, version FROM complaint").query().singleRow())
                .containsEntry("complaint_no", FIRST).containsEntry("status", "UNDER_REVIEW").containsEntry("version", 1);
        assertThat(jdbc.sql("SELECT old_value, new_value FROM complaint_revision").query().singleRow())
                .containsEntry("old_value", TITLE).containsEntry("new_value", "Updated report about the water main");
        assertThat(emailRecipients(FIRST, "EDITED")).containsExactly(properties.admin().email());
        mvc.perform(complaint(post(edit), "mahesh", "Stale title").param("version", "0"))
                .andExpect(redirectedUrl("/complaints/" + FIRST));
        assertThat(count("complaint_revision")).isEqualTo(1);
        mvc.perform(complaint(post(edit), "mahesh", TITLE).param("version", "bad"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "version"));
        mvc.perform(complaint(post(edit), "mahesh", "Corrected report about the water main").param("version", "1"))
                .andExpect(redirectedUrl("/complaints/" + FIRST));
        assertThat(jdbc.sql("SELECT complaint_no, version FROM complaint").query().singleRow())
                .containsEntry("complaint_no", FIRST).containsEntry("version", 2);
        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO complaint (complaint_no, owner_id, category_id, area_id, title, description, address,
                                       status, created_at, updated_at)
                SELECT complaint_no, owner_id, category_id, area_id, title, description, address,
                       status, created_at, updated_at FROM complaint
                """).update()).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uq_complaint_no");
    }

    @Test
    @DisplayName("TC-SM-19, TC-RT-27, TC-RT-28, TC-UC-12a, TC-UC-12b withdrawal validates reason and notifies recipients")
    void withdrawValidatesReasonAndNotifiesOnlyAdminsAndAssignedOfficers() throws Exception {
        demo.seed();
        String submitted = "GRV-2026-000003";
        String assigned = "GRV-2026-000005";
        String underReview = "GRV-2026-000008";
        jdbc.sql("UPDATE complaint SET department_id = :department WHERE complaint_no = :no")
                .param("department", lookup("department", "Water Supply")).param("no", assigned).update();
        String withdrawal = BASE + "/" + submitted + "/withdraw";
        mvc.perform(get(withdrawal).with(Logins.appUser(identities, "sneha@example.com")))
                .andExpect(status().isOk()).andExpect(view().name("complaint/action-form"))
                .andExpect(model().attribute("action", "WITHDRAW"));
        mvc.perform(post(withdrawal).with(Logins.appUser(identities, "sneha@example.com")).with(csrf())
                        .param("version", "0").param("remark", "short"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "remark"));
        mvc.perform(post(withdrawal).with(Logins.appUser(identities, "sneha@example.com")).with(csrf())
                        .param("version", "0").param("remark", "Problem solved by neighbours"))
                .andExpect(redirectedUrl("/complaints/" + submitted));
        mvc.perform(post(BASE + "/" + assigned + "/withdraw").with(Logins.appUser(identities, "jayesh@example.com"))
                        .with(csrf()).param("version", "1").param("remark", "Problem solved by neighbours"))
                .andExpect(redirectedUrl("/complaints/" + assigned));
        mvc.perform(post(BASE + "/" + underReview + "/withdraw").with(Logins.appUser(identities, "sneha@example.com"))
                        .with(csrf()).param("version", "1").param("remark", "Problem solved by neighbours"))
                .andExpect(redirectedUrl("/complaints/" + underReview));
        assertThat(emailRecipients(underReview, "STATUS_CHANGED")).containsExactly(properties.admin().email());
        assertThat(emailRecipients(submitted, "STATUS_CHANGED")).containsExactly(properties.admin().email());
        assertThat(emailRecipients(assigned, "STATUS_CHANGED")).containsExactlyInAnyOrder(
                properties.admin().email(), "anita.deshmukh@example.com", "rohan.kulkarni@example.com");
        assertThat(jdbc.sql("""
                SELECT e.from_status, e.remark FROM complaint_event e JOIN complaint c ON c.id = e.complaint_id
                WHERE c.complaint_no IN (:numbers) AND c.status = 'WITHDRAWN' AND e.to_status = 'WITHDRAWN'
                """).param("numbers", List.of(submitted, assigned, underReview)).query().listOfRows())
                .hasSize(3).allSatisfy(row -> assertThat(row).containsEntry("remark", "Problem solved by neighbours"))
                .extracting(row -> row.get("from_status")).containsExactlyInAnyOrder("SUBMITTED", "ASSIGNED", "UNDER_REVIEW");
    }

    @Test
    @DisplayName("TC-RT-25, TC-RT-27, TC-RT-28 owner-action guards refuse visible non-owners, wrong statuses and stale forms")
    void editAndWithdrawalRefusalsPreserveState() throws Exception {
        demo.seed();
        var owner = Logins.appUser(identities, "mahesh@example.com");
        var duplicateOwner = Logins.appUser(identities, "jayesh@example.com");
        var detail = "/complaints/" + FIRST;
        var edit = BASE + "/" + FIRST + "/edit";
        var withdraw = BASE + "/" + FIRST + "/withdraw";
        String reason = "Problem solved by neighbours";
        long countBefore = count("complaint_event");
        mvc.perform(get(edit).with(duplicateOwner)).andExpect(redirectedUrl(detail))
                .andExpect(flash().attributeExists("flashError"));
        mvc.perform(get(edit).with(owner)).andExpect(redirectedUrl(detail))
                .andExpect(flash().attributeExists("flashError"));
        mvc.perform(get(withdraw).with(owner)).andExpect(redirectedUrl(detail))
                .andExpect(flash().attributeExists("flashError"));
        mvc.perform(post(withdraw).with(owner).with(csrf()).param("version", "1").param("remark", reason))
                .andExpect(redirectedUrl(detail)).andExpect(flash().attributeExists("flashError"));
        jdbc.sql("UPDATE complaint SET status = 'SUBMITTED', department_id = NULL WHERE complaint_no = :no")
                .param("no", FIRST).update();
        mvc.perform(get(withdraw).with(owner)).andExpect(redirectedUrl(detail))
                .andExpect(flash().attribute("flashError", "Other citizens are following this complaint; it cannot be withdrawn."));
        mvc.perform(post(withdraw).with(owner).with(csrf()).param("version", "1").param("remark", reason))
                .andExpect(redirectedUrl(detail))
                .andExpect(flash().attribute("flashError", "Other citizens are following this complaint; it cannot be withdrawn."));
        var other = BASE + "/GRV-2026-000003/withdraw";
        mvc.perform(post(other).with(Logins.appUser(identities, "sneha@example.com")).with(csrf())
                        .param("version", "1").param("remark", reason))
                .andExpect(redirectedUrl("/complaints/GRV-2026-000003")).andExpect(flash().attributeExists("flashError"));
        assertThat(count("complaint_event")).isEqualTo(countBefore);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBMITTED", "UNDER_REVIEW"})
    @DisplayName("TC-SM-20, TC-BR-09, TC-UC-11b edit records revisions and preserves the removed file")
    void editUpdatesFieldsAndRetainsTheRemovedFile(String status) throws Exception {
        mvc.perform(complaint(multipart(BASE).file(photo()), "mahesh", TITLE)).andExpect(status().isFound());
        jdbc.sql("UPDATE complaint SET status = :status").param("status", status).update();
        long old = jdbc.sql("SELECT id FROM attachment").query(Long.class).single();
        var pdf = new MockMultipartFile("files", "report.pdf", "application/pdf",
                new ClassPathResource("files/doc.pdf").getContentAsByteArray());
        String title = "Updated report about the water main";
        String address = "Near the south junction in Kothrud";
        mvc.perform(multipart(BASE + "/" + FIRST + "/edit").file(pdf)
                        .with(Logins.appUser(identities, "mahesh@example.com")).with(csrf())
                        .param("categoryId", lookup("category", "Garbage")).param("areaId", lookup("area", "Kothrud"))
                        .param("title", title).param("description", DESCRIPTION).param("address", address)
                        .param("version", "0").param("removeAttachmentIds", Long.toString(old))
                        .param("changeReason", "Clarified the location"))
                .andExpect(redirectedUrl("/complaints/" + FIRST));
        assertThat(jdbc.sql("SELECT old_value, new_value FROM complaint_revision ORDER BY id")
                .query().listOfRows()).satisfiesExactly(
                        changed -> assertThat(changed).containsEntry("old_value", TITLE).containsEntry("new_value", title),
                        changed -> assertThat(changed).containsEntry("old_value", ADDRESS).containsEntry("new_value", address),
                        removed -> assertThat(removed).containsEntry("old_value", "photo.png").containsEntry("new_value", null),
                        added -> assertThat(added).containsEntry("old_value", null).containsEntry("new_value", "report.pdf"));
        assertThat(jdbc.sql("SELECT deleted, deleted_by, deleted_at FROM attachment WHERE id = :old")
                .param("old", old).query().singleRow()).containsEntry("deleted", true)
                .hasEntrySatisfying("deleted_by", actor -> assertThat(actor).isEqualTo(
                        identities.loadUserByUsername("mahesh@example.com").id()))
                .hasEntrySatisfying("deleted_at", at -> assertThat(at).isNotNull());
        assertThat(uploadedFileCount()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT status, version FROM complaint").query().singleRow())
                .containsEntry("status", status).containsEntry("version", 1);
        assertThat(jdbc.sql("SELECT remark FROM complaint_event WHERE event_type = 'EDITED'").query(String.class).single())
                .isEqualTo("Clarified the location");
        assertThat(emailRecipients(FIRST, "EDITED")).containsExactly(properties.admin().email());
    }

    @Test
    @DisplayName("TC-SM-01 the created event belongs to the verified owner and only active admins are notified")
    void recordsCreatedEventAndNotifiesActiveRecipients() throws Exception {
        for (boolean active : List.of(true, false)) {
            jdbc.sql("""
                    INSERT INTO app_user (full_name, email, mobile, password_hash, role, active, mobile_verified, created_at, updated_at)
                    SELECT full_name, :email, :mobile, password_hash, role, :active, TRUE, created_at, updated_at
                    FROM app_user WHERE email = :primary
                    """).param("email", active ? "active.admin@example.test" : "inactive.admin@example.test")
                    .param("mobile", active ? "7992000111" : "7992000112").param("active", active)
                    .param("primary", properties.admin().email()).update();
        }
        mvc.perform(complaint(multipart(BASE).file(photo()), "mahesh", TITLE))
                .andExpect(redirectedUrl(BASE + "/" + FIRST + "/submitted"));
        assertThat(jdbc.sql("SELECT last_number FROM complaint_sequence WHERE seq_year = 2026")
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT event_type, from_status, to_status, actor_id FROM complaint_event").query().singleRow())
                .containsEntry("event_type", "CREATED").containsEntry("from_status", null)
                .containsEntry("to_status", "SUBMITTED")
                .containsEntry("actor_id", identities.loadUserByUsername("mahesh@example.com").id());
        assertThat(emailRecipients(FIRST, "CREATED")).containsExactlyInAnyOrder(
                "mahesh@example.com", properties.admin().email(), "active.admin@example.test");
        assertThat(count("attachment")).isEqualTo(1);
        assertThat(uploadedFileCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-Q-22, TC-Q-23 allocation continues an existing year and starts a new year at one")
    void sequenceRowsForExistingAndNewYears() {
        jdbc.sql("INSERT INTO complaint_sequence (seq_year, last_number) VALUES (2026, 13)").update();
        List<Integer> allocated = transaction.execute(status -> List.of(numbers.next(2026), numbers.next(2027)));
        assertThat(allocated).containsExactly(14, 1);
        assertThat(jdbc.sql("SELECT last_number FROM complaint_sequence ORDER BY seq_year").query(Integer.class).list())
                .containsExactly(14, 1);
    }

    @Test
    @DisplayName("TC-VE-10 missing and invalid versions have a summary message without a link")
    void editVersionErrorsAppearOnlyInSummary() throws Exception {
        mvc.perform(complaint(multipart(BASE), "mahesh", TITLE)).andExpect(status().isFound());
        for (String version : List.of("", "x")) {
            var result = mvc.perform(complaint(multipart(BASE + "/" + FIRST + "/edit"), "mahesh", TITLE)
                            .param("version", version))
                    .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "version")).andReturn();
            var page = Html.of(result.getResponse().getContentAsString());
            assertThat(page.text("#error-summary li")).isEqualTo("This page is out of date. Reload it and try again.");
            assertThat(page.document().select("#error-summary a, #version-error")).isEmpty();
        }
    }

    private AbstractMockHttpServletRequestBuilder<?> complaint(AbstractMockHttpServletRequestBuilder<?> request, String user, String title) {
        return request.with(Logins.appUser(identities, user + "@example.com")).with(csrf())
                .param("categoryId", lookup("category", "Garbage")).param("areaId", lookup("area", "Kothrud"))
                .param("title", title).param("description", DESCRIPTION).param("address", ADDRESS);
    }

    private String lookup(String table, String name) {
        return jdbc.sql("SELECT id FROM " + table + " WHERE name = :name").param("name", name).query(String.class).single();
    }

    private long count(String table) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single();
    }

    private long uploadedFileCount() {
        try (var paths = Files.walk(properties.upload().dir())) {
            return paths.filter(Files::isRegularFile).count();
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    private java.util.List<String> emailRecipients(String number, String event) {
        return jdbc.sql("""
                SELECT u.email FROM notification n JOIN app_user u ON u.id = n.recipient_id
                JOIN complaint c ON c.id = n.complaint_id JOIN complaint_event e ON e.id = n.event_id
                WHERE c.complaint_no = :no AND e.event_type = :event
                """).param("no", number).param("event", event).query(String.class).list();
    }

    private static MockMultipartFile photo() throws Exception {
        return new MockMultipartFile("files", "photo.png", "image/png",
                new ClassPathResource("files/photo.png").getContentAsByteArray());
    }
}
