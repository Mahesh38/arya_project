package com.smartgrievance.history;

import com.smartgrievance.complaint.ComplaintStatus;
import com.smartgrievance.user.Role;
import java.time.LocalDateTime;
import java.util.List;

public record TimelineEntry(ComplaintHistory.Event type, ComplaintStatus fromStatus, ComplaintStatus toStatus,
                            String actorLabel, Role actorRole, String departmentName, String relatedNo,
                            boolean relatedLinkable, String remark, LocalDateTime createdAt, List<FieldChange> changes) {
    public record FieldChange(String field, String oldValue, String newValue) {}
}
