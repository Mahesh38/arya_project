package com.smartgrievance.complaint;

import com.smartgrievance.access.ComplaintAccessPolicy.Relation;
import com.smartgrievance.common.PageResult;
import com.smartgrievance.common.web.AdminPageLinks;
import com.smartgrievance.history.ComplaintHistory.Event;
import com.smartgrievance.history.TimelineEntry;
import java.util.List;
import java.util.Map;
import com.smartgrievance.seed.ComplaintDemoData;
import com.smartgrievance.seed.DemoData;
import com.smartgrievance.testsupport.Html;
import com.smartgrievance.testsupport.LogCapture;
import com.smartgrievance.testsupport.Logins;
import com.smartgrievance.testsupport.TestDatabase;
import com.smartgrievance.testsupport.WebTestBase;
import com.smartgrievance.user.AccountBootstrap;
import com.smartgrievance.user.AppUserDetailsService;
import com.smartgrievance.workflow.TransitionRules.Action;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ComplaintTrackingWebTest extends WebTestBase {
    private static final String OWNER = "mahesh@example.com";
    private static final String LINKED = "jayesh@example.com";
    private static final String OTHER = "sneha@example.com";
    private static final String OFFICER = "anita.deshmukh@example.com";
    private static final String FIRST = "GRV-2026-000001";
    private static final String FIFTH = "GRV-2026-000005";
    private static final String MINE = "/citizen/complaints";
    private static final String WATCHING = "/citizen/watching";

    @RegisterExtension final LogCapture logs = new LogCapture();

    @Autowired JdbcClient jdbc;
    @Autowired AppUserDetailsService identities;
    @Autowired AccountBootstrap bootstrap;
    @Autowired DemoData users;
    @Autowired ComplaintDemoData complaints;
    @Autowired ComplaintRepository repository;

    @Test
    @DisplayName("TC-RT-20, TC-UC-13a My Complaints preserves filters, clamps paging and excludes other owners")
    void myComplaints() throws Exception {
        demo();
        MvcResult result = mvc.perform(get(MINE).param("status", "FOO").param("page", "0").param("size", "500")
                        .with(Logins.appUser(identities, OWNER))).andExpect(status().isOk())
                .andExpect(view().name("citizen/my-complaints")).andExpect(model().attribute("verifyBanner", false))
                .andReturn();
        var page = page(result);
        assertThat(page.size()).isEqualTo(50);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.totalItems()).isEqualTo(4);
        assertThat(page.items()).extracting(ComplaintListItem::complaintNo)
                .containsExactly(FIRST, "GRV-2026-000009", "GRV-2026-000004", "GRV-2026-000011");
        for (var complaint : page.items()) {
            mvc.perform(get("/complaints/" + complaint.complaintNo()).with(Logins.appUser(identities, OWNER)))
                    .andExpect(status().isOk());
        }
        result = mvc.perform(get(MINE).param("status", "RESOLVED").param("q", "streetlight").param("size", "1")
                        .with(Logins.appUser(identities, OWNER))).andExpect(status().isOk()).andReturn();
        assertThat(page(result).totalItems()).isEqualTo(2);
        var links = (AdminPageLinks) result.getModelAndView().getModel().get("pageLinks");
        assertThat(links.next()).contains("status=RESOLVED", "q=streetlight", "size=1", "page=2");
        assertThat(numbers(mvc.perform(get(MINE).param("q", "grv-2026-000004")
                .with(Logins.appUser(identities, OWNER))).andExpect(status().isOk()).andReturn()))
                .containsExactly("GRV-2026-000004");
        mvc.perform(get(MINE).with(Logins.appUser(identities, "rahul@example.com")))
                .andExpect(status().isOk()).andExpect(model().attribute("verifyBanner", true))
                .andExpect(content().string(containsString("No complaints yet")));
    }

    @Test
    @DisplayName("TC-Q-01, TC-Q-02, TC-Q-03, TC-Q-04, TC-Q-05 and TC-Q-26 tracking query fixtures")
    void fixtureQueries() throws Exception {
        fixture();
        assertThat(numbers(mvc.perform(get(MINE).with(Logins.appUser(identities, OWNER)))
                        .andExpect(status().isOk()).andReturn())).containsExactly(FIRST, "GRV-2026-000004", "GRV-2026-000011");
        var resolved = mvc.perform(get(MINE).param("status", "RESOLVED").with(Logins.appUser(identities, OWNER)))
                .andExpect(status().isOk()).andReturn();
        assertThat(page(resolved).totalItems()).isEqualTo(2);
        var watched = mvc.perform(get(WATCHING).param("q", "ignored").with(Logins.appUser(identities, LINKED)))
                .andExpect(status().isOk()).andExpect(view().name("citizen/watching")).andReturn();
        assertThat(numbers(watched)).containsExactly(FIRST);
        var empty = mvc.perform(get(WATCHING).with(Logins.appUser(identities, OTHER))).andExpect(status().isOk())
                .andExpect(content().string(containsString("You are not watching any complaints."))).andReturn();
        assertThat(page(empty).totalItems()).isZero();
        var first = repository.find(FIRST, false).orElseThrow();
        var fifth = repository.find(FIFTH, false).orElseThrow();
        assertThat(repository.viewerFacts(first.id(), identities.loadUserByUsername(LINKED).id()))
                .isEqualTo(new ComplaintRepository.ViewerFacts(true, true));
        assertThat(repository.viewerFacts(fifth.id(), identities.loadUserByUsername(OTHER).id()))
                .isEqualTo(new ComplaintRepository.ViewerFacts(false, false));
        var search = mvc.perform(get(MINE).param("q", "50%").with(Logins.appUser(identities, OTHER)))
                .andExpect(status().isOk()).andReturn();
        assertThat(numbers(search)).containsExactly("GRV-2026-000012");
    }

    @Test
    @DisplayName("TC-RT-29, TC-Q-14, TC-UT-11, TC-UC-14b, TC-UC-14c, TC-UC-14d detail projects ordered, viewer-specific timeline and revisions")
    void detailTimeline() throws Exception {
        fixture();
        var owner = detail(FIRST, OWNER);
        assertThat(owner.viewerRelation()).isEqualTo(Relation.OWNER);
        assertThat(owner.timeline()).extracting(TimelineEntry::createdAt).isSorted();
        assertThat(owner.timeline()).extracting(TimelineEntry::actorLabel).contains("You", "Grievance Cell", "Water Supply Dept.");
        var edited = owner.timeline().stream().filter(entry -> entry.type() == Event.EDITED).findFirst().orElseThrow();
        assertThat(edited.changes()).extracting(TimelineEntry.FieldChange::field).containsExactly("title", "address");
        var linked = detail(FIRST, LINKED);
        assertThat(linked.ownerName()).isNull();
        assertThat(linked.actions()).containsExactly(Action.UNWATCH);
        assertThat(linked.timeline()).extracting(TimelineEntry::actorLabel).contains("The complainant", "Grievance Cell")
                .noneMatch(label -> label.contains("Deshmukh"));
        assertThat(linked.timeline()).filteredOn(entry -> entry.type() == Event.DUPLICATE_RECEIVED)
                .allSatisfy(entry -> {
                    assertThat(entry.relatedNo()).isNull();
                    assertThat(entry.remark()).isNull();
                });
        var officer = detail(FIRST, OFFICER);
        assertThat(officer.ownerName()).isEqualTo("Mahesh Joshi");
        assertThat(officer.timeline()).extracting(TimelineEntry::actorLabel)
                .contains("Anita Deshmukh (Officer, Water Supply)", "Mahesh Joshi (Citizen)");
        assertThat(officer.actions()).containsExactlyInAnyOrder(Action.ADD_REMARK, Action.RESOLVE, Action.REJECT, Action.MARK_DUPLICATE);
        var admin = detail(FIRST, "admin@example.com");
        assertThat(admin.viewerRelation()).isEqualTo(Relation.ADMIN);
        assertThat(admin.linkedDuplicateCount()).isEqualTo(1);
        assertThat(admin.timeline()).filteredOn(entry -> entry.type() == Event.DUPLICATE_RECEIVED)
                .singleElement().satisfies(entry -> {
                    assertThat(entry.relatedNo()).isEqualTo("GRV-2026-000002");
                    assertThat(entry.relatedLinkable()).isTrue();
                });
        var duplicate = detail("GRV-2026-000002", LINKED);
        assertThat(duplicate.duplicateOf().complaintNo()).isEqualTo(FIRST);
    }

    @Test
    @DisplayName("TC-UC-14a the resolved owner sees review facts, timeline, and only citizen review actions")
    void resolvedOwnerReviewFacts() throws Exception {
        demo();
        String number = "GRV-2026-000004";
        var result = mvc.perform(get("/complaints/" + number).with(Logins.appUser(identities, OWNER)))
                .andExpect(status().isOk()).andExpect(view().name("complaint/detail"))
                .andExpect(content().string(containsString("Electrical Dept."))).andReturn();
        var details = (ComplaintDetailView) result.getModelAndView().getModel().get("c");
        assertThat(details.status()).isEqualTo(ComplaintStatus.RESOLVED);
        assertThat(details.departmentName()).isEqualTo("Electrical");
        assertThat(details.reviewDeadline()).isEqualTo(details.resolvedAt().plusDays(properties.complaint().reviewWindowDays()));
        assertThat(details.actions()).containsExactlyInAnyOrder(Action.CLOSE, Action.REOPEN);
        assertThat(details.overdue()).isFalse();
        assertThat(details.timeline()).extracting(TimelineEntry::createdAt).isSorted();
    }

    @Test
    @DisplayName("TC-SEC-20, TC-SEC-21, TC-SEC-25, TC-UC-14e other owners see uniform 404 and watchers cannot edit")
    void objectAccess() throws Exception {
        fixture();
        var forbidden = mvc.perform(get("/complaints/" + FIRST).with(Logins.appUser(identities, OTHER)))
                .andExpect(status().isNotFound()).andReturn();
        var missing = mvc.perform(get("/complaints/GRV-2026-999999").with(Logins.appUser(identities, OTHER)))
                .andExpect(status().isNotFound()).andReturn();
        assertThat(forbidden.getResponse().getContentAsString()).isEqualTo(missing.getResponse().getContentAsString());
        long actorId = identities.loadUserByUsername(OTHER).id();
        assertThat(logs.entries()).anySatisfy(entry -> {
            assertThat(entry.getLoggerName()).isEqualTo("SECURITY");
            assertThat(entry.getFormattedMessage()).isEqualTo(
                    "NOT_VISIBLE object=complaint ref=" + FIRST + " userId=" + actorId + " role=CITIZEN");
        });
        mvc.perform(get(MINE + "/" + FIRST + "/submitted").with(Logins.appUser(identities, LINKED)))
                .andExpect(status().isNotFound());
        mvc.perform(get(MINE + "/" + FIRST + "/edit").with(Logins.appUser(identities, LINKED)))
                .andExpect(redirectedUrl("/complaints/" + FIRST)).andExpect(flash().attributeExists("flashError"));
    }

    @Test
    @DisplayName("TC-RT-21, TC-BR-18, TC-UC-13b and TC-UC-13c watching provides only detail links the citizen can open")
    void watchingLinks() throws Exception {
        fixture();
        var result = mvc.perform(get(WATCHING).with(Logins.appUser(identities, LINKED)))
                .andExpect(status().isOk()).andReturn();
        assertThat(page(result).totalItems()).isEqualTo(1);
        assertThat(numbers(result)).containsExactly(FIRST);
        detail(FIRST, LINKED);
    }

    @Test
    @DisplayName("TC-NF-06 populated complaint lists cap page size and clamp page numbers")
    void pagingLimits() throws Exception {
        fixture();
        for (int index = 100; index < 160; index++) {
            jdbc.sql("""
                    INSERT INTO complaint (complaint_no, owner_id, category_id, area_id, title, description, address,
                                           status, created_at, updated_at)
                    SELECT :no, owner_id, category_id, area_id, title, description, address, 'SUBMITTED', created_at, updated_at
                    FROM complaint WHERE complaint_no = :template
                    """).param("no", "GRV-2026-%06d".formatted(index)).param("template", FIRST).update();
        }
        var sizes = Map.of("0", 1, "10", 10, "50", 50, "51", 50, "500", 50, "abc", 10);
        for (var size : sizes.entrySet()) {
            var result = mvc.perform(get(MINE).param("size", size.getKey()).with(Logins.appUser(identities, OWNER)))
                    .andExpect(status().isOk()).andReturn();
            assertThat(page(result).items()).hasSize(size.getValue());
            assertThat(page(result).totalItems()).isEqualTo(63);
        }
        for (String invalid : List.of("0", "-1", "99")) {
            var result = mvc.perform(get(MINE).param("page", invalid).with(Logins.appUser(identities, OWNER)))
                    .andExpect(status().isOk()).andReturn();
            assertThat(page(result).page()).isEqualTo(invalid.equals("99") ? 7 : 1);
        }
    }

    @Test
    @DisplayName("TC-SEC-43 and TC-SEC-44 text searches are literal and tracking escapes contributed text")
    void escapedInputAndOutput() throws Exception {
        fixture();
        String title = "A sign reading <sample> with 50% extra markings";
        jdbc.sql("UPDATE complaint SET title = :title WHERE complaint_no = :no")
                .param("title", title).param("no", FIRST).update();
        var results = mvc.perform(get(MINE).param("q", "'_!%").with(Logins.appUser(identities, OWNER)))
                .andExpect(status().isOk()).andReturn();
        assertThat(page(results).items()).isEmpty();
        for (String path : List.of(MINE, WATCHING, "/complaints/" + FIRST)) {
            String viewer = path.equals(WATCHING) ? LINKED : OWNER;
            var result = mvc.perform(get(path).with(Logins.appUser(identities, viewer)))
                    .andExpect(status().isOk()).andReturn();
            String html = result.getResponse().getContentAsString();
            assertThat(html).contains("&lt;sample&gt;");
            assertThat(Html.of(html).document().select("sample")).isEmpty();
        }
    }

    private void fixture() {
        TestDatabase.load(dataSource, "sql/q-fixture.sql");
    }

    private void demo() {
        bootstrap.ensureAdmin(properties.admin(), false);
        users.seedUsers();
        complaints.seed();
    }

    private ComplaintDetailView detail(String no, String email) throws Exception {
        var result = mvc.perform(get("/complaints/" + no).with(Logins.appUser(identities, email)))
                .andExpect(status().isOk()).andExpect(view().name("complaint/detail")).andReturn();
        return (ComplaintDetailView) result.getModelAndView().getModel().get("c");
    }

    @SuppressWarnings("unchecked")
    private static PageResult<ComplaintListItem> page(MvcResult result) {
        return (PageResult<ComplaintListItem>) result.getModelAndView().getModel().get("page");
    }

    private static List<String> numbers(MvcResult result) {
        return page(result).items().stream().map(ComplaintListItem::complaintNo).toList();
    }
}
