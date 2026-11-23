package com.smartgrievance.complaint;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ComplaintDatesTest {
    @Test
    @DisplayName("TC-UT-13 overdue days count complete 24-hour periods")
    void countsCompleteDays() {
        var due = LocalDateTime.of(2026, 11, 21, 23, 0);
        assertThat(ComplaintDates.daysOverdue(due, due.minusDays(1))).isZero();
        assertThat(ComplaintDates.daysOverdue(due, due.plusHours(2))).isZero();
        assertThat(ComplaintDates.daysOverdue(due, due.plusDays(1))).isEqualTo(1);
        assertThat(ComplaintDates.daysOverdue(due, due.plusDays(3).plusHours(11))).isEqualTo(3);
    }
}
