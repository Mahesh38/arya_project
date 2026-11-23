package com.smartgrievance.history;

import static com.smartgrievance.testsupport.Logins.appUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartgrievance.testsupport.Html;
import com.smartgrievance.testsupport.TestDatabase;
import com.smartgrievance.testsupport.WebTestBase;
import com.smartgrievance.user.AppUserDetailsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** FR-17: every revision of an edit is listed in the timeline, for the owner and for staff (doc 10 §7). */
class TimelineRevisionWebTest extends WebTestBase {
    @Autowired AppUserDetailsService identities;

    @Test
    @DisplayName("TC-UC-11a, FR-17 the timeline lists each changed field with its old and new value")
    void editRevisionsAreListed() throws Exception {
        TestDatabase.load(dataSource, "sql/q-fixture.sql");
        for (String viewer : new String[] {"mahesh@example.com", "admin@example.com"}) {
            var result = mvc.perform(get("/complaints/GRV-2026-000001").with(appUser(identities, viewer)))
                    .andExpect(status().isOk()).andReturn();
            var edit = Html.of(result).document().select("ol li:has(span:containsOwn(Complaint edited))").first();
            assertThat(edit).as(viewer).isNotNull();
            var changes = edit.select("ul > li").eachText();
            assertThat(changes).as(viewer).containsExactlyInAnyOrder(
                    "Title changed from Pipeline leaking to Pipeline leaking on Karve Road",
                    "Address changed from Karve Road to Karve Road, near Kothrud Depot");
            assertThat(edit.select("del").eachText()).containsExactlyInAnyOrder("Pipeline leaking", "Karve Road");
            assertThat(edit.select("ins").eachText())
                    .containsExactlyInAnyOrder("Pipeline leaking on Karve Road", "Karve Road, near Kothrud Depot");
        }
    }
}
