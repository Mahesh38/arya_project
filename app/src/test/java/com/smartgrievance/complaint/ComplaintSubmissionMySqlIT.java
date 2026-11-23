package com.smartgrievance.complaint;

import com.smartgrievance.common.exception.FormRejectedException;
import com.smartgrievance.seed.DemoData;
import com.smartgrievance.testsupport.FlowTestBase;
import com.smartgrievance.user.AccountBootstrap;
import com.smartgrievance.user.AppUserDetailsService;
import com.smartgrievance.user.CurrentUser;
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
class ComplaintSubmissionMySqlIT extends FlowTestBase {
    @Autowired ComplaintService service;
    @Autowired JdbcClient jdbc;
    @Autowired AppUserDetailsService identities;
    @Autowired AccountBootstrap bootstrap;
    @Autowired DemoData demo;

    @BeforeEach
    void accounts() {
        bootstrap.ensureAdmin(properties.admin(), false);
        demo.seedUsers();
    }

    @Test
    @DisplayName("TC-BR-27 concurrent first complaints get sequential numbers and IST timestamps in a +05:30 session")
    void concurrentFirstComplaintsUseDistinctSequentialNumbers() throws Exception {
        var form = form();
        var first = identities.loadUserByUsername("mahesh@example.com");
        var second = identities.loadUserByUsername("sneha@example.com");
        assertThat(race(() -> submit(form, first), () -> submit(form, second)))
                .containsExactlyInAnyOrder("GRV-2026-000001", "GRV-2026-000002");
        assertThat(jdbc.sql("SELECT last_number FROM complaint_sequence WHERE seq_year = 2026")
                .query(Integer.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT created_at FROM complaint").query(LocalDateTime.class).list())
                .containsExactly(LocalDateTime.of(2026, 11, 23, 10, 0), LocalDateTime.of(2026, 11, 23, 10, 0));
        assertThat(jdbc.sql("SELECT @@session.time_zone").query(String.class).single()).isEqualTo("+05:30");
    }

    @Test
    @DisplayName("TC-VE-30 two parallel submissions for the last daily slot never exceed the BR-88 limit by more than one")
    void concurrentSubmissionsCannotBothTakeFinalDailySlot() throws Exception {
        var actor = identities.loadUserByUsername("mahesh@example.com");
        var form = form();
        for (int i = 0; i < 9; i++) submit(form, actor);
        assertThat(race(() -> submit(form, actor), () -> submit(form, actor)))
                .containsExactlyInAnyOrder("GRV-2026-000010", "validation.complaint.dailyLimit");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM complaint").query(Long.class).single()).isEqualTo(10);
    }

    private ComplaintForm form() {
        var form = new ComplaintForm();
        form.setCategoryId(jdbc.sql("SELECT id FROM category WHERE name = 'Garbage'").query(Long.class).single());
        form.setAreaId(jdbc.sql("SELECT id FROM area WHERE name = 'Kothrud'").query(Long.class).single());
        form.setTitle("Garbage collection at the corner");
        form.setDescription("The garbage has been accumulating at the junction since Monday.");
        form.setAddress("Karve Road, Kothrud");
        return form;
    }

    private String submit(ComplaintForm form, CurrentUser actor) {
        try {
            return service.submit(form, List.of(), actor);
        } catch (FormRejectedException rejection) {
            return rejection.errors().getFirst().key();
        }
    }

    private static List<String> race(Callable<String> first, Callable<String> second) throws Exception {
        var ready = new CyclicBarrier(2);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var one = executor.submit(() -> {
                ready.await(5, TimeUnit.SECONDS);
                return first.call();
            });
            var two = executor.submit(() -> {
                ready.await(5, TimeUnit.SECONDS);
                return second.call();
            });
            return List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS));
        }
    }
}
