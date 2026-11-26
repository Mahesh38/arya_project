package com.smartgrievance.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartgrievance.testsupport.HttpTestBase;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** D131: the H2 console of the local profile works on localhost without weakening the application's own headers. */
@TestPropertySource(properties = {"spring.h2.console.enabled=true", "spring.h2.console.settings.web-allow-others=false"})
class H2ConsoleHttpTest extends HttpTestBase {
    private static final Pattern SESSION = Pattern.compile("jsessionid=([0-9a-f]+)");

    @Test
    @DisplayName("TC-SEC-53, D131 the H2 console opens on localhost, logs in to the database and keeps the app's CSP elsewhere")
    void consoleWorksOnLocalhost() throws Exception {
        var page = http.get("/h2-console/");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("<title>H2 Console</title>", "login.jsp?jsessionid=");
        assertThat(page.headers().firstValue("x-frame-options")).hasValue("SAMEORIGIN");
        assertThat(page.headers().firstValue("content-security-policy")).isEmpty();

        var matcher = SESSION.matcher(page.body());
        assertThat(matcher.find()).isTrue();
        String form = "language=en&setting=Generic+H2+%28Embedded%29&name=Generic+H2+%28Embedded%29"
                + "&driver=org.h2.Driver&url=" + URLEncoder.encode(currentUrl(), StandardCharsets.UTF_8)
                + "&user=sa&password=";
        var login = http.send(http.request("/h2-console/login.do?jsessionid=" + matcher.group(1))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build());
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.body()).doesNotContain("Wrong user name or password").containsIgnoringCase("frame");

        var app = http.get("/login");
        assertThat(app.headers().firstValue("content-security-policy")).isPresent();
        assertThat(app.headers().firstValue("x-frame-options")).hasValue("DENY");
    }

    private String currentUrl() throws Exception {
        try (var connection = dataSource.getConnection()) {
            return connection.getMetaData().getURL();
        }
    }
}
