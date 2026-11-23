package com.smartgrievance.notification;

import java.time.LocalDateTime;

public record NotificationItem(long id, String message, boolean read, LocalDateTime createdAt, String complaintNo) {}
