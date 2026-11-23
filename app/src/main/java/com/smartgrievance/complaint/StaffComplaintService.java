package com.smartgrievance.complaint;

import com.smartgrievance.common.PageResult;
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
    private final StaffComplaintRepository complaints;
    private final Clock clock;
    private final int reviewDays;

    StaffComplaintService(StaffComplaintRepository complaints, Clock clock, AppProperties properties) {
        this.complaints = complaints;
        this.clock = clock;
        this.reviewDays = properties.complaint().reviewWindowDays();
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
