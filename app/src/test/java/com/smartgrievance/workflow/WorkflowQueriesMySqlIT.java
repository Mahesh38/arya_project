package com.smartgrievance.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;

/** TC-Q-40: the same query tests as {@link ComplaintWorkflowWebTest}, run on MySQL 8.x by ./mvnw verify -Pmysql. */
@Tag("mysql")
@ActiveProfiles({"test", "mysqltest"})
@DisplayName("TC-Q-40 workflow statements Q18 and Q21 on MySQL")
class WorkflowQueriesMySqlIT extends ComplaintWorkflowWebTest {
}
