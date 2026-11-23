package com.smartgrievance.complaint;

import java.time.LocalDateTime;

public record ComplaintListItem(String complaintNo, String title, String categoryName, String areaName,
                                String departmentName, ComplaintStatus status, LocalDateTime createdAt,
                                boolean overdue, LocalDateTime dueAt, int daysOverdue, LocalDateTime reviewDeadline) {}
