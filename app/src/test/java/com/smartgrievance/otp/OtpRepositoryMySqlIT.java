package com.smartgrievance.otp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;

/** TC-Q-40: the same query tests as {@link OtpRepositoryTest}, run on MySQL 8.x by ./mvnw verify -Pmysql. */
@Tag("mysql")
@ActiveProfiles({"test", "mysqltest"})
@DisplayName("TC-Q-40 OTP statements Q30 on MySQL")
class OtpRepositoryMySqlIT extends OtpRepositoryTest {
}
