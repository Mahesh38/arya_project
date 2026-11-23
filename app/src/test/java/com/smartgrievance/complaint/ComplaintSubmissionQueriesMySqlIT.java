package com.smartgrievance.complaint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;

/** TC-Q-40: the same query tests as {@link ComplaintSubmissionWebTest}, run on MySQL 8.x by ./mvnw verify -Pmysql. */
@Tag("mysql")
@ActiveProfiles({"test", "mysqltest"})
@DisplayName("TC-Q-40 submission statements Q19-Q29 on MySQL")
class ComplaintSubmissionQueriesMySqlIT extends ComplaintSubmissionWebTest {
}
