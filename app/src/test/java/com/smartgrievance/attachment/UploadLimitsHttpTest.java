package com.smartgrievance.attachment;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.smartgrievance.seed.DemoData;
import com.smartgrievance.testsupport.Html;
import com.smartgrievance.testsupport.HttpTestBase;
import com.smartgrievance.testsupport.HttpTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

class UploadLimitsHttpTest extends HttpTestBase {
    private static final String CREATE = "/citizen/complaints";
    private static final String FORM = CREATE + "/new";
    private static final String TOTAL_LIMIT = "The files are too large together.";
    private static final int MB = 1024 * 1024;
    @Autowired DemoData demo;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void citizen() {
        demo.seedUsers();
    }

    @Test
    @DisplayName("TC-SEC-38 oversized multipart protects the form and only flashes in an existing session")
    void oversizedRequestsAreRejectedAtTheCorrectBoundary() throws Exception {
        signIn();
        byte[] largerThanRequestLimit = new byte[27 * MB];
        var oversized = http.send(http.multipart(CREATE, Map.of(), "sample.png", "image/png",
                largerThanRequestLimit).build());
        assertThat(oversized.statusCode()).isEqualTo(302);
        assertThat(URI.create(oversized.headers().firstValue("location").orElseThrow()).getPath()).isEqualTo(FORM);
        assertThat(Html.of(http.get(FORM).body()).text("main")).contains(TOTAL_LIMIT);
        assertThat(Html.of(http.get(FORM).body()).text("main")).doesNotContain(TOTAL_LIMIT);

        var otherRoute = http.send(http.multipart("/account/password", Map.of(), "sample.txt", "text/plain",
                largerThanRequestLimit).build());
        assertThat(otherRoute.statusCode()).isEqualTo(413);
        assertThat(Html.of(otherRoute.body()).text("h1")).isEqualTo("Request too large");

        var anonymous = new HttpTestClient(port);
        var stale = anonymous.send(anonymous.multipart(CREATE, Map.of(), "sample.png", "image/png",
                largerThanRequestLimit).header("Cookie", "JSESSIONID=expired-fixture").build());
        assertThat(stale.statusCode()).isEqualTo(302);
        assertThat(stale.headers().allValues("set-cookie")).isEmpty();
        assertThat(URI.create(stale.headers().firstValue("location").orElseThrow()).getPath()).isEqualTo(FORM);
        var after = anonymous.send(anonymous.request(FORM).header("Cookie", "JSESSIONID=expired-fixture").GET().build());
        assertThat(after.statusCode()).isEqualTo(302);
        assertThat(after.headers().firstValue("location")).hasValueSatisfying(url -> assertThat(url).endsWith("/login?expired"));
    }

    @Test
    @DisplayName("TC-SEC-38a a six-megabyte file reaches the form validator without losing typed fields")
    void singleFileOverFiveMiBReachesTheForm() throws Exception {
        signIn();
        var form = http.get(FORM);
        assertThat(form.statusCode()).isEqualTo(200);
        String title = "Large attachment example";
        var category = jdbc.sql("SELECT id FROM category WHERE name = 'Garbage'").query(Long.class).single();
        var area = jdbc.sql("SELECT id FROM area WHERE name = 'Kothrud'").query(Long.class).single();
        var submitted = http.send(http.multipart(CREATE, Map.of("_csrf", csrf(form.body()),
                "title", title, "categoryId", category.toString(), "areaId", area.toString(),
                "description", "The kerbside collection point has several bags waiting for collection.",
                "address", "Near the ward office junction"), "large.png", "image/png", new byte[6 * MB]).build());

        assertThat(submitted.statusCode()).isEqualTo(200);
        var page = Html.of(submitted.body());
        assertThat(page.text("#files-error")).contains("large.png is larger than 5 MB.");
        assertThat(page.document().selectFirst("#title").val()).isEqualTo(title);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM complaint WHERE title = :title")
                .param("title", title).query(Long.class).single()).isZero();
    }

    private void signIn() throws Exception {
        String token = csrf(http.get("/login").body());
        String body = "email=" + encoded("mahesh@example.com") + "&password=" + encoded("Demo@1234")
                + "&_csrf=" + encoded(token);
        var login = http.send(http.request("/login").header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
        assertThat(login.statusCode()).isEqualTo(302);
        assertThat(login.headers().firstValue("location")).hasValueSatisfying(url -> assertThat(url).endsWith(CREATE));
    }

    private static String csrf(String page) {
        return Html.of(page).document().selectFirst("input[name=_csrf]").val();
    }

    private static String encoded(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
