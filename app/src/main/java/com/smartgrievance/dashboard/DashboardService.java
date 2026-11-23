package com.smartgrievance.dashboard;

import com.smartgrievance.complaint.ComplaintStatus;
import com.smartgrievance.complaint.StaffComplaintService.SummaryCard;
import com.smartgrievance.dashboard.DashboardRepository.CountRow;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Builds the admin dashboard (UC-50, FR-60 to FR-62, FR-64; doc 09 §5.7). */
@Service
@Transactional(readOnly = true)
public class DashboardService {
    static final String ALL = "/admin/complaints";
    static final int OVERDUE_LIMIT = 10;
    private static final List<ComplaintStatus> CARD_STATUSES = List.of(ComplaintStatus.ASSIGNED,
            ComplaintStatus.IN_PROGRESS, ComplaintStatus.REOPENED, ComplaintStatus.RESOLVED, ComplaintStatus.CLOSED,
            ComplaintStatus.REJECTED, ComplaintStatus.DUPLICATE, ComplaintStatus.WITHDRAWN);

    public record CountLine(Long id, String name, long open, long closed, String openUrl, String closedUrl) {}
    public record OverdueItem(String complaintNo, String title, ComplaintStatus status, String departmentName,
                              int slaDays, long daysElapsed, long daysOverdue) {}
    public record DashboardView(LocalDateTime asOf, List<SummaryCard> cards, List<CountLine> byCategory,
                                List<CountLine> byDepartment, List<CountLine> byArea, List<OverdueItem> overdue,
                                long overdueTotal, String chartJson) {}

    private final DashboardRepository repository;
    private final Clock clock;
    private final JsonMapper json;

    DashboardService(DashboardRepository repository, Clock clock, JsonMapper json) {
        this.repository = repository;
        this.clock = clock;
        this.json = json;
    }

    public DashboardView adminDashboard() {
        var now = LocalDateTime.now(clock);
        var counts = repository.countByStatus();
        long overdueTotal = repository.countOverdue(now);
        var cards = new ArrayList<SummaryCard>();
        cards.add(new SummaryCard("Total", counts.values().stream().mapToLong(Long::longValue).sum(), ALL, "card-total"));
        cards.add(new SummaryCard("Pending", counts.get(ComplaintStatus.SUBMITTED) + counts.get(ComplaintStatus.UNDER_REVIEW),
                ALL + "?status=PENDING", "status-group-pending"));
        for (var status : CARD_STATUSES) {
            cards.add(new SummaryCard(status.label(), counts.get(status), ALL + "?status=" + status.name(), status.badgeClass()));
        }
        cards.add(new SummaryCard("Overdue", overdueTotal, ALL + "?overdue=true", "badge-overdue"));

        var byCategory = lines(repository.byCategory(), "categoryId");
        var byArea = lines(repository.byArea(), "areaId");
        var byDepartment = lines(departmentsWithZeroRows(repository.byDepartment()), "departmentId");
        var overdue = repository.overdueList(now, OVERDUE_LIMIT).stream()
                .map(row -> new OverdueItem(row.complaintNo(), row.title(), row.status(), row.departmentName(),
                        row.slaDays(), days(row.createdAt(), now), days(row.dueAt(), now)))
                .toList();
        return new DashboardView(now, List.copyOf(cards), byCategory, byDepartment, byArea, overdue, overdueTotal,
                chartJson(byDepartment));
    }

    /** Every department appears (zero rows added, BR doc 08 §5.2), then "(Not assigned)" last. */
    private List<CountRow> departmentsWithZeroRows(List<CountRow> counted) {
        Map<Long, CountRow> byId = new LinkedHashMap<>();
        CountRow unassigned = null;
        for (var row : counted) {
            if (row.id() == null) unassigned = row;
            else byId.put(row.id(), row);
        }
        var rows = new ArrayList<CountRow>();
        for (var department : repository.departments()) {
            rows.add(byId.getOrDefault(department.id(), department));
        }
        rows.add(unassigned != null ? new CountRow(null, "(Not assigned)", unassigned.open(), unassigned.closed())
                : new CountRow(null, "(Not assigned)", 0, 0));
        return rows;
    }

    private static List<CountLine> lines(List<CountRow> rows, String parameter) {
        return rows.stream().map(row -> {
            String value = row.id() == null ? "NONE" : row.id().toString();
            String base = ALL + "?" + parameter + "=" + value + "&status=";
            return new CountLine(row.id(), row.name(), row.open(), row.closed(), base + "NOT_FINAL", base + "FINAL");
        }).toList();
    }

    private static long days(LocalDateTime from, LocalDateTime now) {
        return Math.max(0, Duration.between(from, now).toDays());
    }

    /** FR-64: the by-department numbers once more for Chart.js, written into a data attribute (no inline script, D64). */
    private String chartJson(List<CountLine> byDepartment) {
        var chart = new LinkedHashMap<String, Object>();
        chart.put("labels", byDepartment.stream().map(CountLine::name).toList());
        chart.put("open", byDepartment.stream().map(CountLine::open).toList());
        chart.put("closed", byDepartment.stream().map(CountLine::closed).toList());
        return json.writeValueAsString(chart);
    }
}
