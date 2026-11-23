package com.smartgrievance.duplicate;

import com.smartgrievance.common.exception.ActionRefusedException;
import com.smartgrievance.complaint.ComplaintRepository;
import com.smartgrievance.complaint.ComplaintStatus;
import com.smartgrievance.seed.ComplaintDemoData;
import com.smartgrievance.seed.DemoData;
import com.smartgrievance.testsupport.FlowTestBase;
import com.smartgrievance.user.AccountBootstrap;
import com.smartgrievance.user.AppUserDetailsService;
import com.smartgrievance.watch.WatchService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("mysql")
@ActiveProfiles({"test", "mysqltest"})
class DuplicateMySqlIT extends FlowTestBase {
    private static final String ORIGINAL = "GRV-2026-000001";
    private static final String A = "GRV-2026-000005";
    private static final String B = "GRV-2026-000007";
    private static final String NOTE = "The reports describe the same location and problem.";
    @Autowired AccountBootstrap bootstrap;
    @Autowired DemoData users;
    @Autowired ComplaintDemoData demo;
    @Autowired AppUserDetailsService identities;
    @Autowired ComplaintRepository complaints;
    @Autowired DuplicateService duplicates;
    @Autowired WatchService watchers;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void seed() {
        bootstrap.ensureAdmin(properties.admin(), false);
        users.seedUsers();
        demo.seed();
    }

    @Test
    @DisplayName("TC-SC-02, TC-Q-28 linking inserts a watcher and watch again reactivates the same key on MySQL")
    void linksAndReactivatesOnMySql() {
        var source = complaints.find(A, false).orElseThrow();
        var target = complaints.find(ORIGINAL, false).orElseThrow();
        var owner = identities.loadUserByUsername("jayesh@example.com");
        var admin = identities.loadUserByUsername("admin@example.com");
        jdbc.sql("DELETE FROM complaint_watcher WHERE complaint_id = :id AND user_id = :user")
                .param("id", target.id()).param("user", owner.id()).update();
        duplicates.markDuplicate(A, ORIGINAL, source.version(), NOTE, admin);
        assertThat(complaints.find(A, false).orElseThrow().originalNo()).isEqualTo(ORIGINAL);
        assertThat(watchers.unwatch(ORIGINAL, owner)).isTrue();
        testClock.set(LocalDateTime.now(testClock).plusMinutes(1));
        watchers.watch(ORIGINAL, owner);
        var stored = jdbc.sql("SELECT active, since FROM complaint_watcher WHERE complaint_id = :id AND user_id = :user")
                .param("id", target.id()).param("user", owner.id()).query().singleRow();
        assertThat(stored).containsEntry("active", true).containsEntry("since", LocalDateTime.now(testClock));
    }

    @Test
    @DisplayName("Opposite-direction duplicate requests cannot create a cycle on MySQL")
    void oppositeDirectionsSerialize() throws Exception {
        var first = complaints.find(A, false).orElseThrow();
        var second = complaints.find(B, false).orElseThrow();
        var start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.of(link(start, first, second), link(start, second, first)),
                    15, TimeUnit.SECONDS);
            assertThat(results.getFirst().get()).isNotEqualTo(results.getLast().get());
        }
        var end = List.of(complaints.find(A, false).orElseThrow(), complaints.find(B, false).orElseThrow());
        assertThat(end).filteredOn(row -> row.status() == ComplaintStatus.DUPLICATE).hasSize(1);
        assertThat(end).filteredOn(row -> row.originalNo() != null).hasSize(1);
    }

    private Callable<Boolean> link(CyclicBarrier start, ComplaintRepository.Complaint source,
                                   ComplaintRepository.Complaint target) {
        return () -> {
            start.await(5, TimeUnit.SECONDS);
            try {
                duplicates.markDuplicate(source.number(), target.number(), source.version(), NOTE,
                        identities.loadUserByUsername("admin@example.com"));
                return true;
            } catch (ActionRefusedException expected) {
                return false;
            }
        };
    }
}
