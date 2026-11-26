package com.smartgrievance.dashboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;

/** TC-Q-40: the same query tests as {@link DashboardRepositoryTest}, run on MySQL 8.x by ./mvnw verify -Pmysql. */
@Tag("mysql")
@ActiveProfiles({"test", "mysqltest"})
@DisplayName("TC-Q-40 dashboard aggregates Q08-Q13 on MySQL")
class DashboardRepositoryMySqlIT extends DashboardRepositoryTest {
}
