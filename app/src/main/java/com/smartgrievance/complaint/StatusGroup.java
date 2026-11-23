package com.smartgrievance.complaint;

import static com.smartgrievance.complaint.ComplaintStatus.*;

import java.util.Locale;
import java.util.Set;

public enum StatusGroup {
    PENDING("Pending (new + under review)", Set.of(SUBMITTED, UNDER_REVIEW)),
    ACTIVE("Active", Set.of(ASSIGNED, IN_PROGRESS, REOPENED, RESOLVED)),
    NOT_FINAL("Open (not finished)", Set.of(SUBMITTED, UNDER_REVIEW, ASSIGNED, IN_PROGRESS, REOPENED, RESOLVED)),
    FINAL("Finished", Set.of(CLOSED, REJECTED, DUPLICATE, WITHDRAWN)),
    ALL("All statuses", Set.of(ComplaintStatus.values()));

    private final String label;
    private final Set<ComplaintStatus> statuses;

    StatusGroup(String label, Set<ComplaintStatus> statuses) {
        this.label = label;
        this.statuses = statuses;
    }

    public static Set<ComplaintStatus> expand(String filter) {
        if (filter == null) return Set.of();
        for (StatusGroup group : values()) {
            if (group.name().equals(filter)) return group.statuses();
        }
        for (ComplaintStatus status : ComplaintStatus.values()) {
            if (status.name().equals(filter)) return Set.of(status);
        }
        return Set.of();
    }

    public String label() { return label; }
    public Set<ComplaintStatus> statuses() { return statuses; }
    public String badgeClass() { return "status-group-" + name().toLowerCase(Locale.ROOT).replace('_', '-'); }
}
