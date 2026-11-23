package com.smartgrievance.complaint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;

/** TC-Q-40: the same query tests as {@link StaffComplaintWebTest}, run on MySQL 8.x by ./mvnw verify -Pmysql. */
@Tag("mysql")
@ActiveProfiles({"test", "mysqltest"})
@DisplayName("TC-Q-40 queue and admin list Q06-Q07 on MySQL")
class StaffComplaintQueriesMySqlIT extends StaffComplaintWebTest {
}
