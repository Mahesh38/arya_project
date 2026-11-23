package com.smartgrievance.complaint;

import java.time.LocalDateTime;

public record ComplaintSummary(String complaintNo, String title, ComplaintStatus status, String categoryName,
                               String areaName, String departmentName, int version, LocalDateTime reviewDeadline) {}
