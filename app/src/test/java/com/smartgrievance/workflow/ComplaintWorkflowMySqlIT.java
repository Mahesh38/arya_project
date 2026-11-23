package com.smartgrievance.workflow;

import com.smartgrievance.common.exception.ConcurrentUpdateException;
import com.smartgrievance.complaint.ComplaintRepository;
import com.smartgrievance.seed.ComplaintDemoData;
import com.smartgrievance.seed.DemoData;
import com.smartgrievance.testsupport.FlowTestBase;
import com.smartgrievance.user.AccountBootstrap;
import com.smartgrievance.user.AppUserDetailsService;
import com.smartgrievance.user.CurrentUser;
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

import static com.smartgrievance.complaint.ComplaintStatus.RESOLVED;
import static com.smartgrievance.workflow.TransitionRules.Action.RESOLVE;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("mysql")
@ActiveProfiles({"test", "mysqltest"})
class ComplaintWorkflowMySqlIT extends FlowTestBase {
    private static final String FIRST = "GRV-2026-000001";
    @Autowired AccountBootstrap bootstrap;
    @Autowired DemoData users;
    @Autowired ComplaintDemoData demo;
    @Autowired AppUserDetailsService identities;
    @Autowired ComplaintWorkflowService workflow;
    @Autowired ComplaintRepository complaints;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void seed() {
        bootstrap.ensureAdmin(properties.admin(), false);
        users.seedUsers();
        demo.seed();
    }

    @Test
    @DisplayName("TC-SM-42 concurrent resolve forms commit only one transition on MySQL")
    void staleConcurrentResolutionCannotInsertAnotherEvent() throws Exception {
        var prior = complaints.find(FIRST, false).orElseThrow();
        var anita = identities.loadUserByUsername("anita.deshmukh@example.com");
        var rohan = identities.loadUserByUsername("rohan.kulkarni@example.com");
        var start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.of(resolve(start, prior.version(), anita), resolve(start, prior.version(), rohan)),
                    15, TimeUnit.SECONDS);
            assertThat(results.getFirst().get()).isNotEqualTo(results.getLast().get());
        }
        var current = complaints.find(FIRST, false).orElseThrow();
        assertThat(current.status()).isEqualTo(RESOLVED);
        assertThat(current.version()).isEqualTo(prior.version() + 1);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM complaint_event WHERE complaint_id = :id AND to_status = 'RESOLVED'")
                .param("id", prior.id()).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("""
                SELECT COUNT(*) FROM notification n JOIN complaint_event e ON e.id = n.event_id
                WHERE n.complaint_id = :id AND e.to_status = 'RESOLVED'
                """).param("id", prior.id()).query(Long.class).single()).isEqualTo(1);
    }

    private Callable<Boolean> resolve(CyclicBarrier start, int version, CurrentUser officer) {
        return () -> {
            start.await(5, TimeUnit.SECONDS);
            try {
                workflow.perform(FIRST, RESOLVE, version, "Work has been completed today", null, List.of(), officer);
                return true;
            } catch (ConcurrentUpdateException expected) {
                return false;
            }
        };
    }
}
