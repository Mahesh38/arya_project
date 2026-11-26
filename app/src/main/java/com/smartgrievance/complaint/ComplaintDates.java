package com.smartgrievance.complaint;

import java.time.Duration;
import java.time.LocalDateTime;

final class ComplaintDates {
    private ComplaintDates() {}

    static int daysOverdue(LocalDateTime due, LocalDateTime now) {
        return Math.toIntExact(Math.max(0, Duration.between(due, now).toDays()));
    }
}
