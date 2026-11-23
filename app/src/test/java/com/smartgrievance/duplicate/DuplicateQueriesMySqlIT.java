package com.smartgrievance.duplicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;

/** TC-Q-40: the same query tests as {@link DuplicateAndWatchWebTest}, run on MySQL 8.x by ./mvnw verify -Pmysql. */
@Tag("mysql")
@ActiveProfiles({"test", "mysqltest"})
@DisplayName("TC-Q-40 watcher statements Q17 and Q28 on MySQL")
class DuplicateQueriesMySqlIT extends DuplicateAndWatchWebTest {
}
