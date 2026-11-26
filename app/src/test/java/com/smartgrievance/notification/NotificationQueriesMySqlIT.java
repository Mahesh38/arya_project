package com.smartgrievance.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;

/** TC-Q-40: the same query tests as {@link NotificationWebTest}, run on MySQL 8.x by ./mvnw verify -Pmysql. */
@Tag("mysql")
@ActiveProfiles({"test", "mysqltest"})
@DisplayName("TC-Q-40 notification statements Q15-Q31 on MySQL")
class NotificationQueriesMySqlIT extends NotificationWebTest {
}
