package com.smartgrievance.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.smartgrievance.testsupport.Html;
import com.smartgrievance.testsupport.LogCapture;
import com.smartgrievance.testsupport.WebTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.Import;

@Import(ErrorTriggerController.class)
class ErrorMappingWebTest extends WebTestBase {
    @RegisterExtension
    LogCapture logs = new LogCapture();

    @ParameterizedTest
    @CsvSource({"not-found,404,404", "form-rejected,500,500", "storage,500,500", "upload-size,413,4xx",
            "integrity,500,500", "database,500,500", "not-acceptable,406,4xx", "media-type,415,4xx"})
    @DisplayName("TC-VE-14 application errors map to the documented status and view")
    void mapsErrors(String error, int code, String template) throws Exception {
        var response = mvc.perform(get("/test-only/errors/{error}", error).with(user("test").roles("CITIZEN")))
                .andExpect(status().is(code)).andExpect(view().name("error/" + template))
                .andExpect(model().attribute("status", code)).andReturn();
        assertThat(Html.of(response).hasNoForm()).isTrue();
        assertThat(response.getResponse().getContentAsString()).doesNotContain(ErrorTriggerController.PRIVATE_MESSAGE);
    }

    @Test
    @DisplayName("TC-VE-14 a refusal redirects with a flash message")
    void redirectWithFlash() throws Exception {
        mvc.perform(get("/test-only/errors/refused").with(user("test").roles("CITIZEN")))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/complaints/GRV-2026-000001"))
                .andExpect(flash().attributeExists("flashError"));
    }

    @Test
    @DisplayName("TC-VE-11 bad form fields and request parameter types are handled")
    void bindingErrors() throws Exception {
        mvc.perform(post("/test-only/form").with(user("test")).with(csrf())
                        .param("slaDays", "abc").param("departmentId", "abc"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("slaDays=")))
                .andExpect(content().string(containsString("departmentId=")));
        mvc.perform(get("/test-only/typed").with(user("test")).param("id", "abc"))
                .andExpect(status().isBadRequest()).andExpect(model().attribute("titleKey", "page.error.400.title"));
        mvc.perform(get("/test-only/typed/abc").with(user("test")))
                .andExpect(status().isNotFound()).andExpect(view().name("error/404"));
    }

    @Test
    @DisplayName("TC-VE-12 query parameters normalize while path variables keep their original text")
    void normalizedQueryVersusPath() throws Exception {
        mvc.perform(get("/test-only/lookup/grv-2026-000001").with(user("test"))
                        .param("email", " Mahesh.J@Example.COM "))
                .andExpect(status().isOk()).andExpect(content().string("grv-2026-000001|mahesh.j@example.com"));
    }

    @Test
    @DisplayName("TC-VE-31 bean validation messages use fixed English")
    void language() throws Exception {
        mvc.perform(post("/test-only/form").with(user("test")).with(csrf())
                        .header("Accept-Language", "de-DE,hi-IN").param("mobile", "123"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("10-digit")));
    }

    @Test
    @DisplayName("TC-SEC-32 server errors expose a reference without underlying diagnostics")
    void serverErrorReference() throws Exception {
        var result = mvc.perform(get("/test-only/errors/unexpected").with(user("test")))
                .andExpect(status().isInternalServerError()).andExpect(view().name("error/500")).andReturn();
        String reference = Html.of(result).text("#error-reference");
        assertThat(reference).matches("ERR-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}");
        assertThat(logs.entries().stream().filter(log -> log.getFormattedMessage().contains(reference)))
                .singleElement().satisfies(log -> assertThat(log.getMDCPropertyMap()).containsEntry("ref", reference));
        assertThat(result.getResponse().getContentAsString()).doesNotContain(ErrorTriggerController.PRIVATE_MESSAGE);

        mvc.perform(get("/notifications/unread-count").with(user("test")))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.error").value("server_error"))
                .andExpect(jsonPath("$.reference").isString()).andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    @DisplayName("TC-VE-20 an unsupported method keeps the Allow header on its error page")
    void methodNotAllowed() throws Exception {
        mvc.perform(post("/test-only/typed").with(user("test")).with(csrf()))
                .andExpect(status().isMethodNotAllowed()).andExpect(header().string("Allow", "GET"))
                .andExpect(model().attribute("titleKey", "page.error.405.title"));
    }
}
