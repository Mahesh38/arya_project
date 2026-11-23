package com.smartgrievance.workflow;

import com.smartgrievance.complaint.ComplaintRepository;
import com.smartgrievance.duplicate.DuplicateService;
import com.smartgrievance.history.ComplaintHistory;
import com.smartgrievance.history.ComplaintHistory.Event;
import com.smartgrievance.notification.NotificationService;
import com.smartgrievance.notification.NotificationService.WorkflowNotice;
import com.smartgrievance.seed.ComplaintDemoData;
import com.smartgrievance.seed.DemoData;
import com.smartgrievance.testsupport.FlowTestBase;
import com.smartgrievance.testsupport.LogCapture;
import com.smartgrievance.user.AccountBootstrap;
import com.smartgrievance.user.AppUserDetailsService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static com.smartgrievance.complaint.ComplaintStatus.*;
import static com.smartgrievance.workflow.TransitionRules.Action.RESOLVE;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkflowRollbackTest extends FlowTestBase {
    private static final String FIRST = "GRV-2026-000001";
    @RegisterExtension final LogCapture logs = new LogCapture();
    @Autowired AccountBootstrap bootstrap;
    @Autowired DemoData users;
    @Autowired ComplaintDemoData demo;
    @Autowired AppUserDetailsService identities;
    @Autowired ComplaintWorkflowService workflow;
    @Autowired DuplicateService duplicates;
    @Autowired AutoCloseJob job;
    @Autowired JdbcClient jdbc;
    @MockitoSpyBean ComplaintRepository complaints;
    @MockitoSpyBean ComplaintHistory history;
    @MockitoSpyBean NotificationService notifications;

    @BeforeEach
    void seed() {
        bootstrap.ensureAdmin(properties.admin(), false);
        users.seedUsers();
        demo.seed();
    }

    @ParameterizedTest(name = "TC-BR-24, TC-NF-08 resolve rollback after {0} failure")
    @ValueSource(strings = {"event", "notification"})
    void transactionalResolveRollsBackFilesAndRows(String failingStep) throws Exception {
        var before = complaints.find(FIRST, false).orElseThrow();
        var officer = identities.loadUserByUsername("anita.deshmukh@example.com");
        var initialFiles = files();
        var initialTables = snapshot();
        var fixture = new MockMultipartFile("files", "completed.png", "image/png",
                new ClassPathResource("files/photo.png").getContentAsByteArray());
        var failure = new IllegalStateException("Injected " + failingStep + " failure");
        if (failingStep.equals("event")) {
            doThrow(failure).when(history).record(eq(before.id()), eq(Event.STATUS_CHANGED), eq(IN_PROGRESS), eq(RESOLVED),
                    eq(officer.id()), eq(before.departmentId()), isNull(), anyString(), any());
        } else {
            doThrow(failure).when(notifications).workflow(eq(RESOLVE), any(WorkflowNotice.class));
        }
        assertThatThrownBy(() -> workflow.perform(FIRST, RESOLVE, before.version(), "Leak repaired at the junction",
                null, List.of(fixture), officer)).isSameAs(failure);
        assertThat(complaints.find(FIRST, false)).contains(before);
        assertThat(snapshot()).isEqualTo(initialTables);
        assertThat(files()).containsExactlyElementsOf(initialFiles);
    }

    @Test
    @DisplayName("TC-NF-08 duplicate notification failure rolls back the link, both events, and watchers")
    void duplicateLinksRollBackTogether() throws Exception {
        var original = complaints.find(FIRST, false).orElseThrow();
        var source = complaints.find("GRV-2026-000003", false).orElseThrow();
        var admin = identities.loadUserByUsername("admin@example.com");
        var initial = snapshot();
        var initialFiles = files();
        var failure = new IllegalStateException("Injected notification failure");
        doThrow(failure).when(notifications).duplicateLinked(eq(source.id()), anyLong(), eq(source.ownerId()),
                anyList(), eq(source.number()), eq(original.number()), any());
        assertThatThrownBy(() -> duplicates.markDuplicate(source.number(), original.number(), source.version(),
                "Both reports describe the same issue", admin)).isSameAs(failure);
        assertThat(snapshot()).isEqualTo(initial);
        assertThat(files()).isEqualTo(initialFiles);
    }

    @Test
    @DisplayName("TC-SM-46, TC-UC-70b auto-close skips stale rows, rolls back one failure and retries next run")
    void autoCloseContinuesAfterASkippedRowAndFailure() {
        var midnight = LocalDateTime.now(testClock);
        var first = complaints.find("GRV-2026-000011", false).orElseThrow();
        var second = complaints.find("GRV-2026-000004", false).orElseThrow();
        var third = complaints.find("GRV-2026-000001", false).orElseThrow();
        expire(first.id(), midnight.minusDays(11));
        expire(second.id(), midnight.minusDays(10));
        expire(third.id(), midnight.minusDays(9));
        var firstBatch = new AtomicBoolean(true);
        doAnswer(call -> {
            Object rows = call.callRealMethod();
            if (firstBatch.getAndSet(false)) {
                jdbc.sql("UPDATE complaint SET status = 'CLOSED', version = version + 1 WHERE id = :id")
                        .param("id", first.id()).update();
            }
            return rows;
        }).when(complaints).findExpiredResolved(any(), anyInt(), isNull());
        var firstFailure = new AtomicBoolean(true);
        doAnswer(call -> {
            WorkflowNotice notice = call.getArgument(0);
            if (notice.complaintId() == second.id() && firstFailure.getAndSet(false)) {
                throw new IllegalStateException("Injected auto-close notice failure");
            }
            return call.callRealMethod();
        }).when(notifications).autoClosed(any(), anyInt());
        job.run();
        assertThat(complaints.find(first.number(), false).orElseThrow().status()).isEqualTo(CLOSED);
        assertThat(complaints.find(second.number(), false).orElseThrow().version()).isEqualTo(second.version());
        assertThat(complaints.find(second.number(), false).orElseThrow().status()).isEqualTo(RESOLVED);
        assertThat(complaints.find(third.number(), false).orElseThrow().status()).isEqualTo(CLOSED);
        assertThat(autoClosedIds()).containsExactly(third.id());
        assertThat(logs.entries()).anySatisfy(log -> assertThat(log.getFormattedMessage())
                .contains("AUTO_CLOSE_FAILED complaintNo=" + second.number()));
        job.run();
        assertThat(autoClosedIds()).containsExactly(third.id(), second.id());
    }

    private void expire(long id, LocalDateTime resolved) {
        jdbc.sql("UPDATE complaint SET status = 'RESOLVED', resolved_at = :resolved WHERE id = :id")
                .param("resolved", resolved).param("id", id).update();
    }

    private List<Long> autoClosedIds() {
        return jdbc.sql("SELECT complaint_id FROM complaint_event WHERE event_type = 'AUTO_CLOSED' ORDER BY id")
                .query(Long.class).list();
    }

    private List<List<java.util.Map<String, Object>>> snapshot() {
        return List.of("attachment", "complaint_event", "complaint_revision", "complaint_watcher", "notification").stream()
                .map(table -> jdbc.sql("SELECT * FROM " + table).query().listOfRows()).toList();
    }

    private List<Path> files() throws Exception {
        try (var paths = Files.walk(properties.upload().dir())) {
            return paths.filter(Files::isRegularFile).sorted().toList();
        }
    }
}
