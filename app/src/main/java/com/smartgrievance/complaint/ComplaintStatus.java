package com.smartgrievance.complaint;

import java.util.Locale;

public enum ComplaintStatus {
    SUBMITTED("Submitted", "bi-inbox"),
    UNDER_REVIEW("Under Review", "bi-search"),
    ASSIGNED("Assigned", "bi-building"),
    IN_PROGRESS("In Progress", "bi-tools"),
    REOPENED("Reopened", "bi-arrow-counterclockwise"),
    RESOLVED("Resolved", "bi-check2-circle"),
    CLOSED("Closed", "bi-lock"),
    REJECTED("Rejected", "bi-x-circle"),
    DUPLICATE("Duplicate", "bi-files"),
    WITHDRAWN("Withdrawn", "bi-arrow-return-left");

    private final String label;
    private final String icon;

    ComplaintStatus(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    public boolean isFinal() { return StatusGroup.FINAL.statuses().contains(this); }
    public String label() { return label; }
    public String icon() { return icon; }
    public String badgeClass() { return "status-" + name().toLowerCase(Locale.ROOT).replace('_', '-'); }
}
