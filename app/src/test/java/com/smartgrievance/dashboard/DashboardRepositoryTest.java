package com.smartgrievance.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartgrievance.complaint.ComplaintStatus;
import com.smartgrievance.dashboard.DashboardRepository.CountRow;
import com.smartgrievance.dashboard.DashboardRepository.OverdueRow;
import com.smartgrievance.testsupport.RepositoryTestBase;
import com.smartgrievance.testsupport.TestDatabase;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Doc 08 §5.2–5.3 queries on the q-fixture; "now" is 2026-11-22 12:00 as in key-queries.sql. */
class DashboardRepositoryTest extends RepositoryTestBase {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 22, 12, 0);
    private DashboardRepository repository;

    @BeforeEach
    void load() {
        TestDatabase.load(dataSource, "sql/q-fixture.sql");
        repository = new DashboardRepository(jdbc);
    }

    @Test
    @DisplayName("TC-Q-08 count per status includes zero statuses")
    void countsPerStatus() {
        var counts = repository.countByStatus();
        assertThat(counts).hasSize(ComplaintStatus.values().length).containsAllEntriesOf(Map.of(
                ComplaintStatus.SUBMITTED, 1L, ComplaintStatus.UNDER_REVIEW, 0L, ComplaintStatus.ASSIGNED, 3L,
                ComplaintStatus.IN_PROGRESS, 1L, ComplaintStatus.REOPENED, 1L, ComplaintStatus.RESOLVED, 2L,
                ComplaintStatus.CLOSED, 0L, ComplaintStatus.DUPLICATE, 1L, ComplaintStatus.WITHDRAWN, 0L,
                ComplaintStatus.REJECTED, 0L));
        assertThat(counts.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(9);
    }

    @Test
    @DisplayName("TC-Q-09 overdue count for all departments and for Water Supply")
    void countsOverdue() {
        assertThat(repository.countOverdue(NOW)).isEqualTo(4);
        assertThat(repository.countOverdue(NOW, 2)).isEqualTo(1);
        assertThat(repository.countOverdue(LocalDateTime.of(2026, 11, 1, 0, 0))).isZero();
    }

    @Test
    @DisplayName("TC-Q-10 by category lists every category and counts RESOLVED as open")
    void byCategory() {
        assertThat(repository.byCategory()).extracting(CountRow::name, CountRow::open, CountRow::closed).containsExactly(
                org.assertj.core.groups.Tuple.tuple("Drainage", 0L, 0L),
                org.assertj.core.groups.Tuple.tuple("Garbage", 2L, 0L),
                org.assertj.core.groups.Tuple.tuple("Other", 0L, 0L),
                org.assertj.core.groups.Tuple.tuple("Road Damage", 2L, 0L),
                org.assertj.core.groups.Tuple.tuple("Streetlight", 2L, 0L),
                org.assertj.core.groups.Tuple.tuple("Water Leakage", 2L, 1L));
    }

    @Test
    @DisplayName("TC-Q-11 by department includes the (Not assigned) row")
    void byDepartment() {
        var rows = repository.byDepartment();
        assertThat(rows).extracting(CountRow::id, CountRow::open, CountRow::closed).contains(
                org.assertj.core.groups.Tuple.tuple(null, 1L, 0L),
                org.assertj.core.groups.Tuple.tuple(2L, 3L, 1L),
                org.assertj.core.groups.Tuple.tuple(5L, 2L, 0L));
        assertThat(rows.stream().mapToLong(row -> row.open() + row.closed()).sum()).isEqualTo(9);
    }

    @Test
    @DisplayName("TC-Q-12 by area returns 0 / 0 for areas without complaints")
    void byArea() {
        var rows = repository.byArea();
        assertThat(rows).hasSize(7);
        assertThat(rows).filteredOn(row -> row.name().equals("Baner") || row.name().equals("Kasba Peth"))
                .allSatisfy(row -> assertThat(row.open() + row.closed()).isZero());
        assertThat(rows).filteredOn(row -> row.name().equals("Kothrud")).singleElement()
                .satisfies(row -> assertThat(row.open()).isEqualTo(2)).satisfies(row -> assertThat(row.closed()).isEqualTo(1));
    }

    @Test
    @DisplayName("TC-Q-13 overdue list orders by due date and returns due_at, not day counts")
    void overdueList() {
        var rows = repository.overdueList(NOW, 10);
        assertThat(rows).extracting(OverdueRow::complaintNo)
                .containsExactly("GRV-2026-000005", "GRV-2026-000007", "GRV-2026-000013", "GRV-2026-000003");
        assertThat(rows.getFirst().dueAt()).isEqualTo(LocalDateTime.of(2026, 11, 17, 10, 0));
        assertThat(rows.getLast().departmentName()).isNull();
        assertThat(repository.overdueList(NOW, 2)).hasSize(2);
    }
}
