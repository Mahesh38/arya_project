package com.smartgrievance.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest;
import java.util.List;

import com.smartgrievance.testsupport.Html;
import com.smartgrievance.testsupport.LogCapture;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import com.smartgrievance.testsupport.HttpTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Import(CookieAndFirewallHttpTest.ErrorFilterConfig.class)
class CookieAndFirewallHttpTest extends HttpTestBase {
    @RegisterExtension
    LogCapture logs = new LogCapture();

    @Test
    @DisplayName("TC-SEC-35 unsupported methods cannot succeed")
    void unsupportedMethodsDoNotSucceed() throws Exception {
        for (String method : List.of("PUT", "DELETE", "TRACE")) {
            var response = http.send(http.request("/login").method(method, HttpRequest.BodyPublishers.noBody()).build());
            assertThat(response.statusCode()).isGreaterThanOrEqualTo(300);
        }
    }

    @Test
    @DisplayName("TC-VE-20 framework-generated errors render a session-free HTML page")
    void errorPageWithoutSession() throws Exception {
        var response = http.send(http.request("/login").method("TRACE", HttpRequest.BodyPublishers.noBody()).build());
        assertThat(response.statusCode()).isIn(400, 405);
        assertThat(response.headers().allValues("set-cookie")).isEmpty();
        assertThat(response.headers().firstValue("content-type")).hasValueSatisfying(type -> assertThat(type).contains("text/html"));
        assertThat(Html.of(response.body()).hasNoForm()).isTrue();
    }

    @Test
    @DisplayName("TC-VE-18 a filter failure renders a reference without creating a session")
    void filterFailure() throws Exception {
        var response = http.get("/test-only/filter-error");
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().allValues("set-cookie")).isEmpty();
        var page = Html.of(response.body());
        assertThat(page.hasNoForm()).isTrue();
        String reference = page.text("#error-reference");
        assertThat(reference).matches("ERR-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}");
        assertThat(response.body()).doesNotContain("test diagnostic");
        assertThat(logs.entries()).filteredOn(entry -> entry.getFormattedMessage().contains(reference))
                .singleElement().satisfies(entry -> assertThat(entry.getMDCPropertyMap()).containsEntry("ref", reference));
    }

    @Test
    @DisplayName("TC-VE-22 framework JSON errors contain only the configured public keys")
    void errorJsonWithoutDetails() throws Exception {
        var request = http.request("/test-only/filter-error").setHeader("Accept", "application/json").GET().build();
        var response = http.send(request);
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().allValues("set-cookie")).isEmpty();
        var document = new tools.jackson.databind.ObjectMapper().readTree(response.body());
        assertThat(document.propertyNames()).containsExactlyInAnyOrder("timestamp", "status", "error");
        assertThat(response.body()).doesNotContain("test diagnostic");
    }

    @Test
    @DisplayName("TC-SEC-33 the firewall renders a 400 page for a disallowed resource name")
    void disallowedResourceName() throws Exception {
        var response = http.get("/images/item%25count.png");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().allValues("set-cookie")).isEmpty();
        assertThat(Html.of(response.body()).text("h1")).isEqualTo("Request not accepted");
    }

    @Test
    @DisplayName("TC-VE-18 a multipart body above the configured limit is rejected")
    void multipartLimit() throws Exception {
        var request = http.multipart("/login", java.util.Map.of(), "sample.txt", "text/plain",
                new byte[27 * 1024 * 1024]).build();
        var response = http.send(request);
        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(Html.of(response.body()).text("h1")).isEqualTo("Request too large");
    }

    @TestConfiguration
    static class ErrorFilterConfig {
        @Bean
        FilterRegistrationBean<Filter> failingTestFilter() {
            Filter filter = (request, response, chain) -> { throw new ServletException("test diagnostic"); };
            var registration = new FilterRegistrationBean<>(filter);
            registration.addUrlPatterns("/test-only/filter-error");
            registration.setOrder(-200);
            return registration;
        }
    }
}
