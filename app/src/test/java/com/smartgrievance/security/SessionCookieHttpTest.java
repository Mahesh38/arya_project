package com.smartgrievance.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartgrievance.testsupport.HttpTestBase;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "server.servlet.session.cookie.secure=true")
class SessionCookieHttpTest extends HttpTestBase {
    @Test
    @DisplayName("TC-SEC-31 secure session cookie has HttpOnly and SameSite=Lax flags and stays out of URLs")
    void cookieIsPrivate() throws Exception {
        var login = http.get("/login");
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.headers().allValues("set-cookie")).anySatisfy(cookie ->
                assertThat(cookie).contains("JSESSIONID=", "HttpOnly", "Secure", "SameSite=Lax"));
        var page = Jsoup.parse(login.body());
        assertThat(page.select("[href]").eachAttr("href")).allMatch(url -> !url.contains(";jsessionid="));
        assertThat(page.select("form[action]").eachAttr("action")).allMatch(url -> !url.contains(";jsessionid="));
    }
}
