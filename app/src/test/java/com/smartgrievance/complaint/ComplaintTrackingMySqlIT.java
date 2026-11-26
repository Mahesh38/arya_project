package com.smartgrievance.complaint;

import com.smartgrievance.access.ComplaintAccessPolicy.Relation;
import com.smartgrievance.common.PageRequest;
import com.smartgrievance.history.ComplaintHistory.Event;
import com.smartgrievance.testsupport.FlowTestBase;
import com.smartgrievance.testsupport.TestDatabase;
import com.smartgrievance.user.AppUserDetailsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("mysql")
@ActiveProfiles({"test", "mysqltest"})
class ComplaintTrackingMySqlIT extends FlowTestBase {
    @Autowired ComplaintQueryService queries;
    @Autowired AppUserDetailsService users;

    @Test
    @DisplayName("TC-Q-01, TC-Q-02, TC-Q-03, TC-Q-04, TC-Q-05, TC-Q-14 and TC-Q-26 run on MySQL")
    void accessQueriesAndTimelineOnMySql() {
        TestDatabase.load(dataSource, "sql/q-fixture.sql");
        var mahesh = users.loadUserByUsername("mahesh@example.com");
        var sneha = users.loadUserByUsername("sneha@example.com");
        var jayesh = users.loadUserByUsername("jayesh@example.com");
        var filter = CitizenListFilter.of(null, null, new PageRequest(1, 2));
        assertThat(queries.myComplaints(mahesh, filter).totalItems()).isEqualTo(3);
        assertThat(queries.myComplaints(mahesh, filter).items()).extracting(ComplaintListItem::complaintNo)
                .containsExactly("GRV-2026-000001", "GRV-2026-000004");
        var resolved = CitizenListFilter.of("RESOLVED", null, filter.paging());
        assertThat(queries.myComplaints(mahesh, resolved).items()).hasSize(2);
        assertThat(queries.watching(jayesh, filter).items()).extracting(ComplaintListItem::complaintNo)
                .containsExactly("GRV-2026-000001");
        assertThat(queries.watching(sneha, filter).items()).isEmpty();
        var watched = queries.detail("GRV-2026-000001", jayesh);
        assertThat(watched.viewerRelation()).isEqualTo(Relation.LINKED_OWNER);
        assertThat(watched.timeline()).filteredOn(entry -> entry.type() == Event.EDITED)
                .singleElement().satisfies(entry -> assertThat(entry.changes()).hasSize(2));
        assertThat(queries.myComplaints(sneha, CitizenListFilter.of(null, "50%", filter.paging())).items())
                .extracting(ComplaintListItem::complaintNo).containsExactly("GRV-2026-000012");
    }
}
