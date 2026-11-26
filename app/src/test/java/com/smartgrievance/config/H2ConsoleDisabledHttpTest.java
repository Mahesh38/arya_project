package com.smartgrievance.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartgrievance.testsupport.HttpTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Without the local profile the console does not exist: the path is an ordinary protected URL. */
class H2ConsoleDisabledHttpTest extends HttpTestBase {
    @Test
    @DisplayName("TC-SEC-53, D131 outside the local profile there is no H2 console")
    void noConsoleByDefault() throws Exception {
        var response = http.get("/h2-console/");
        assertThat(response.statusCode()).isIn(302, 404);
        assertThat(response.body()).doesNotContain("H2 Console");
    }
}
