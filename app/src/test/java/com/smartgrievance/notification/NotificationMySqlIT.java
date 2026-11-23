package com.smartgrievance.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartgrievance.common.PageRequest;
import com.smartgrievance.testsupport.TestDatabase;
import com.smartgrievance.user.AppUserDetailsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

@Tag("mysql")
@ActiveProfiles({"test", "mysqltest"})
class NotificationMySqlIT extends HousekeepingTest {
    @Autowired NotificationService notifications;
    @Autowired AppUserDetailsService users;

    @Test
    @DisplayName("TC-Q-15, TC-Q-24, TC-Q-27 recipient counts and listing operate on MySQL")
    void readsAndMarksNotifications() {
        TestDatabase.load(dataSource, "sql/q-fixture.sql");
        var recipient = users.loadUserByUsername("mahesh@example.com");
        var other = users.loadUserByUsername("jayesh@example.com");
        assertThat(notifications.unreadCount(recipient)).isEqualTo(3);
        assertThat(notifications.list(recipient, new PageRequest(1, 3)).items())
                .extracting(NotificationItem::id).containsExactly(4L, 3L, 2L);
        assertThat(notifications.open(4, recipient)).contains("GRV-2026-000001");
        assertThat(notifications.unreadCount(recipient)).isEqualTo(2);
        notifications.markAllRead(recipient);
        assertThat(notifications.unreadCount(recipient)).isZero();
        assertThat(notifications.unreadCount(other)).isEqualTo(1);
    }
}
