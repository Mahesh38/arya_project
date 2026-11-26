package com.smartgrievance.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.smartgrievance.testsupport.WebTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class HeadersWebTest extends WebTestBase {
    @Test
    @DisplayName("TC-SEC-30 login and error responses include defense headers")
    void standardHeaders() throws Exception {
        for (String path : new String[]{"/login", "/missing-page"}) {
            var request = get(path).secure(true);
            if (path.equals("/missing-page")) request.with(user("citizen").roles("CITIZEN"));
            mvc.perform(request)
                    .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")))
                    .andExpect(header().string("Referrer-Policy", "same-origin"))
                    .andExpect(header().string("Cross-Origin-Opener-Policy", "same-origin"))
                    .andExpect(header().string("Cross-Origin-Resource-Policy", "same-origin"));
        }
    }

    @Test
    @DisplayName("TC-SEC-30 localhost and administrators can see health without details")
    void localOrAdminHealth() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP")).andExpect(jsonPath("$.components").doesNotExist());
        var remote = (org.springframework.test.web.servlet.request.RequestPostProcessor) request -> {
            request.setRemoteAddr("192.0.2.10");
            return request;
        };
        mvc.perform(get("/actuator/health").with(remote)).andExpect(status().isFound());
        mvc.perform(get("/actuator/health").with(remote).with(user("citizen").roles("CITIZEN")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/actuator/health").with(remote).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-VE-20 a logged-in request without a CSRF token selects the expired-form variant")
    void csrfPageVariant() throws Exception {
        var session = new MockHttpSession();
        var rejected = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/account/password").session(session).with(user("citizen").roles("CITIZEN")))
                .andExpect(status().isForbidden()).andReturn();
        org.assertj.core.api.Assertions.assertThat(rejected.getRequest().getAttribute("app.errorReason"))
                .isEqualTo("csrf");
        mvc.perform(get("/error").with(user("citizen").roles("CITIZEN"))
                        .header("Accept", "text/html")
                        .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, 403)
                        .requestAttr("app.errorReason", "csrf"))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/403"))
                .andExpect(model().attribute("titleKey", "page.error.403.csrf.title"));
    }
}
