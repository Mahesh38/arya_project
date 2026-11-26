package com.smartgrievance.workflow;

import com.smartgrievance.common.exception.ActionRefusedException;
import com.smartgrievance.common.exception.NotFoundException;
import com.smartgrievance.complaint.ComplaintEditForm;
import com.smartgrievance.complaint.ComplaintRepository;
import com.smartgrievance.complaint.ComplaintStatus;
import com.smartgrievance.complaint.ComplaintService;
import com.smartgrievance.complaint.RemarkForm;
import com.smartgrievance.duplicate.DuplicateService;
import com.smartgrievance.seed.ComplaintDemoData;
import com.smartgrievance.seed.DemoData;
import com.smartgrievance.testsupport.FlowTestBase;
import com.smartgrievance.user.AccountBootstrap;
import com.smartgrievance.user.AppUserDetailsService;
import com.smartgrievance.user.CurrentUser;
import com.smartgrievance.watch.WatchService;
import com.smartgrievance.workflow.TransitionRules.Action;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class WorkflowRefusalMatrixTest extends FlowTestBase {
    private static final String FIRST = "GRV-2026-000001";
    private static final String TARGET = "GRV-2026-000005";
    private static final String REMARK = "A valid reason for the requested change";
    @Autowired AccountBootstrap bootstrap;
    @Autowired DemoData users;
    @Autowired ComplaintDemoData demo;
    @Autowired AppUserDetailsService identities;
    @Autowired ComplaintRepository repository;
    @Autowired ComplaintService complaints;
    @Autowired ComplaintWorkflowService workflow;
    @Autowired DuplicateService duplicates;
    @Autowired WatchService watchers;
    @Autowired JdbcClient jdbc;

    @Test
    @DisplayName("TC-SM-32 every denied action in the matrix leaves persisted state unchanged")
    void everyDeniedCellRefusesWithoutWriting() throws Exception {
        bootstrap.ensureAdmin(properties.admin(), false);
        users.seedUsers();
        demo.seed();
        var people = Map.of(
                "O", identities.loadUserByUsername("mahesh@example.com"),
                "W", identities.loadUserByUsername("sneha@example.com"),
                "D", identities.loadUserByUsername("jayesh@example.com"),
                "F", identities.loadUserByUsername("anita.deshmukh@example.com"),
                "A", identities.loadUserByUsername("admin@example.com"),
                "X", identities.loadUserByUsername("rahul@example.com"));
        var original = repository.find(FIRST, false).orElseThrow();
        var target = repository.find(TARGET, false).orElseThrow();
        watchers.unwatch(FIRST, people.get("D"));
        var now = LocalDateTime.now(testClock);
        jdbc.sql("""
                INSERT INTO complaint_watcher (complaint_id, user_id, active, since, updated_at)
                VALUES (:id, :user, TRUE, :now, :now)
                """).param("id", original.id()).param("user", people.get("W").id()).param("now", now).update();
        var denied = TransitionRulesTest.cells().filter(cell -> !cell.expected() && !cell.viewer().code().equals("X")).toList();
        var before = activitySnapshot();
        try {
            for (var status : ComplaintStatus.values()) {
                Long department = switch (status) {
                    case SUBMITTED, UNDER_REVIEW -> null;
                    default -> original.departmentId();
                };
                setStatus(original.id(), status, department, status == ComplaintStatus.DUPLICATE ? target.id() : null,
                        status == ComplaintStatus.RESOLVED ? now.minusDays(1) : null);
                var baseline = repository.find(FIRST, false).orElseThrow();
                for (var cell : denied.stream().filter(entry -> entry.status() == status).toList()) {
                    var actor = people.get(cell.viewer().code());
                    var refusal = catchThrowable(() -> perform(cell.action(), actor, baseline.version(), target.departmentId()));
                    assertThat(refusal).as("%s %s %s", status, cell.action(), cell.viewer().code())
                            .isInstanceOfAny(ActionRefusedException.class, NotFoundException.class);
                }
                assertThat(repository.find(FIRST, false)).contains(baseline);
                assertThat(activitySnapshot()).isEqualTo(before);
            }
        } finally {
            setStatus(original.id(), original.status(), original.departmentId(), null, original.resolvedAt());
        }
    }

    private void perform(Action action, CurrentUser actor, int version, Long department) {
        switch (action) {
            case EDIT -> {
                var form = new ComplaintEditForm();
                form.setVersion(version);
                complaints.edit(FIRST, form, List.of(), actor);
            }
            case WITHDRAW -> {
                var form = new RemarkForm();
                form.setVersion(version);
                form.setRemark(REMARK);
                complaints.withdraw(FIRST, form, actor);
            }
            case WATCH -> watchers.watch(FIRST, actor);
            case UNWATCH -> watchers.unwatch(FIRST, actor);
            case MARK_DUPLICATE -> duplicates.markDuplicate(FIRST, TARGET, version, REMARK, actor);
            default -> workflow.perform(FIRST, action, version, REMARK, department, List.of(), actor);
        }
    }

    private void setStatus(long id, ComplaintStatus status, Long department, Long duplicateOf, LocalDateTime resolvedAt) {
        jdbc.sql("""
                UPDATE complaint SET status = :status, department_id = :department,
                    duplicate_of_id = :duplicate, resolved_at = :resolved WHERE id = :id
                """).param("status", status.name()).param("department", department).param("duplicate", duplicateOf)
                .param("resolved", resolvedAt).param("id", id).update();
    }

    private List<?> activitySnapshot() {
        return List.of("complaint_event", "complaint_revision", "complaint_watcher", "notification").stream()
                .map(table -> jdbc.sql("SELECT * FROM " + table).query().listOfRows()).toList();
    }
}
