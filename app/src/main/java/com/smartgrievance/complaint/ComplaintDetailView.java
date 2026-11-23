package com.smartgrievance.complaint;

import com.smartgrievance.access.ComplaintAccessPolicy.Relation;
import com.smartgrievance.attachment.AttachmentView;
import com.smartgrievance.history.TimelineEntry;
import com.smartgrievance.workflow.TransitionRules.Action;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record ComplaintDetailView(String complaintNo, String title, String description, String address,
        ComplaintStatus status, String categoryName, String areaName, String departmentName, String ownerName,
        LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime resolvedAt, LocalDateTime reviewDeadline,
        int reopenCount, int maxReopens, int version, boolean overdue, LocalDateTime dueAt, int daysOverdue,
        ComplaintRef duplicateOf, int linkedDuplicateCount, Relation viewerRelation, List<AttachmentView> attachments,
        List<AttachmentView> resolutionAttachments, List<TimelineEntry> timeline, Set<Action> actions, List<String> notices) {
    public record ComplaintRef(String complaintNo, String title, ComplaintStatus status) {}

    public boolean can(String actionName) {
        return actions.stream().anyMatch(action -> action.name().equals(actionName));
    }

    public boolean isFinal() { return status.isFinal(); }
    public boolean isResolved() { return status == ComplaintStatus.RESOLVED; }
}
