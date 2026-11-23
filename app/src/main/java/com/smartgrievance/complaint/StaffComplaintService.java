package com.smartgrievance.complaint;

import com.smartgrievance.common.PageResult;
import com.smartgrievance.security.SecurityEvents;
import java.io.IOException;
import java.io.Writer;
import java.time.format.DateTimeFormatter;
import com.smartgrievance.config.AppProperties;
import com.smartgrievance.user.CurrentUser;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StaffComplaintService {
    public record SummaryCard(String label, long count, String url, String style) {}
    public record SummaryCards(List<SummaryCard> cards) {}
    private static final String QUEUE = "/officer/queue";
    private static final List<ComplaintStatus> QUEUE_STATUSES = List.of(ComplaintStatus.ASSIGNED, ComplaintStatus.IN_PROGRESS,
            ComplaintStatus.REOPENED, ComplaintStatus.RESOLVED, ComplaintStatus.CLOSED, ComplaintStatus.REJECTED,
            ComplaintStatus.DUPLICATE, ComplaintStatus.WITHDRAWN);
    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");
    private static final List<String> CSV_HEADER = List.of("Complaint No", "Title", "Category", "Area", "Address",
            "Department", "Status", "Submitted", "Due", "Overdue", "Days overdue", "Resolved");
    private final StaffComplaintRepository complaints;
    private final Clock clock;
    private final int reviewDays;
    private final int exportMaxRows;

    StaffComplaintService(StaffComplaintRepository complaints, Clock clock, AppProperties properties) {
        this.complaints = complaints;
        this.clock = clock;
        this.reviewDays = properties.complaint().reviewWindowDays();
        this.exportMaxRows = properties.export().maxRows();
    }

    public int exportMaxRows() {
        return exportMaxRows;
    }

    /** How many complaints an admin filter matches; R-32 refuses the export above {@link #exportMaxRows()}. */
    public long exportCount(StaffComplaintFilter filter) {
        return complaints.count(filter, LocalDateTime.now(clock));
    }

    /**
     * Writes the CSV of R-32 (FR-65): UTF-8 BOM, header, complaint fields only (no email or mobile, SEC-51), cells
     * escaped against formula injection (SEC-47). Returns the number of data rows written.
     */
    public int exportCsv(StaffComplaintFilter filter, long adminId, Writer out) throws IOException {
        var now = LocalDateTime.now(clock);
        var rows = complaints.export(filter, now, exportMaxRows);
        var csv = new CsvWriter(out).bom();
        csv.row(CSV_HEADER);
        for (var row : rows) {
            csv.row(java.util.Arrays.asList(row.complaintNo(), row.title(), row.category(), row.area(), row.address(),
                    row.department(), row.status().label(), format(row.createdAt()), format(row.dueAt()),
                    row.overdue() ? "Yes" : "No",
                    row.overdue() ? Integer.toString(ComplaintDates.daysOverdue(row.dueAt(), now)) : "",
                    format(row.resolvedAt())));
        }
        out.flush();
        SecurityEvents.LOG.info("ADMIN_ACTION adminId={} action=export_csv targetType=complaint targetId=- rows={}",
                adminId, rows.size());
        return rows.size();
    }

    private static String format(LocalDateTime value) {
        return value == null ? null : CSV_DATE.format(value);
    }

    public PageResult<ComplaintListItem> departmentQueue(CurrentUser officer, StaffComplaintFilter filter) {
        if (officer.departmentId() == null) return new PageResult<>(List.of(), 1, filter.paging().size(), 0);
        return complaints.list(filter, officer.departmentId(), LocalDateTime.now(clock), reviewDays);
    }

    public PageResult<ComplaintListItem> allComplaints(StaffComplaintFilter filter) {
        return complaints.list(filter, null, LocalDateTime.now(clock), reviewDays);
    }

    public SummaryCards officerSummary(CurrentUser officer) {
        if (officer.departmentId() == null) return new SummaryCards(List.of());
        var counts = complaints.departmentCounts(officer.departmentId(), LocalDateTime.now(clock));
        Map<ComplaintStatus, Long> byStatus = counts.stream().collect(Collectors.toMap(
                StaffComplaintRepository.StatusCount::status, StaffComplaintRepository.StatusCount::count));
        var cards = new ArrayList<SummaryCard>();
        for (var status : QUEUE_STATUSES) {
            String label = status == ComplaintStatus.RESOLVED ? "Awaiting citizen" : status.label();
            cards.add(new SummaryCard(label, byStatus.getOrDefault(status, 0L), QUEUE + "?status=" + status.name(), status.badgeClass()));
        }
        cards.add(new SummaryCard("Overdue", counts.stream().mapToLong(StaffComplaintRepository.StatusCount::overdue).sum(),
                QUEUE + "?status=ALL&overdue=true", "badge-overdue"));
        return new SummaryCards(List.copyOf(cards));
    }
}
