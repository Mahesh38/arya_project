package com.smartgrievance.workflow;

import com.smartgrievance.complaint.ComplaintStatus;
import java.time.LocalDateTime;

public record ComplaintFacts(ComplaintStatus status, long ownerId, Long departmentId, int reopenCount,
                             LocalDateTime resolvedAt, boolean hasLinkedDuplicates,
                             boolean viewerIsActiveWatcher, boolean viewerOwnsLinkedDuplicate) {}
