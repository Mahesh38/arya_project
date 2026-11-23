package com.smartgrievance.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import java.util.Map;
import com.smartgrievance.common.web.GlobalExceptionHandler;
import com.smartgrievance.testsupport.Html;
import com.smartgrievance.testsupport.LogCapture;
import com.smartgrievance.testsupport.WebTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

@Import(ErrorTriggerController.class)
class ErrorMappingWebTest extends WebTestBase {
    @RegisterExtension
    LogCapture logs = new LogCapture();
    private final Logger adviceLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private Level previousLevel;

    @BeforeEach
    void captureDebugDiagnostics() {
        previousLevel = adviceLogger.getLevel();
        adviceLogger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void restoreLogLevel() {
        adviceLogger.setLevel(previousLevel);
    }

    @ParameterizedTest
    @CsvSource({
            "not-found,404,404,INFO,NOT_FOUND object=complaint",
            "form-rejected,500,500,ERROR,UNEXPECTED ref=",
            "storage,500,500,ERROR,UNEXPECTED ref=",
            "upload-size,413,4xx,WARN,UPLOAD_REJECTED reason=request_too_large",
            "integrity,500,500,ERROR,UNEXPECTED ref=",
            "unknown-unique,500,500,ERROR,UNEXPECTED ref=",
            "database,500,500,ERROR,UNEXPECTED ref=",
            "not-acceptable,406,4xx,DEBUG,CLIENT_ERROR status=406",
            "media-type,415,4xx,DEBUG,CLIENT_ERROR status=415"
    })
    @DisplayName("TC-VE-14 application errors map to the documented status and view")
    void mapsErrors(String error, int code, String template, String level, String logText) throws Exception {
        var response = mvc.perform(get("/test-only/errors/{error}", error).with(user("test").roles("CITIZEN")))
                .andExpect(status().is(code)).andExpect(view().name("error/" + template))
                .andExpect(model().attribute("status", code)).andReturn();
        assertThat(Html.of(response).hasNoForm()).isTrue();
        assertThat(response.getResponse().getContentAsString()).doesNotContain(ErrorTriggerController.PRIVATE_MESSAGE);
        assertLog(Level.toLevel(level), logText);
        if (error.equals("not-found")) {
            assertThat(logs.entries()).anySatisfy(entry -> {
                assertThat(entry.getLoggerName()).isEqualTo("SECURITY");
                assertThat(entry.getLevel()).isEqualTo(Level.INFO);
                assertThat(entry.getFormattedMessage()).startsWith("NOT_VISIBLE object=complaint");
            });
        }
    }

    @ParameterizedTest
    @CsvSource({"refused,BUSINESS_RULE", "transition,INVALID_TRANSITION", "concurrent,CONCURRENT_UPDATE"})
    @DisplayName("TC-VE-14 a refusal redirects with a flash message")
    void redirectWithFlash(String failure, String code) throws Exception {
        mvc.perform(get("/test-only/errors/{failure}", failure).with(user("test").roles("CITIZEN")))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/complaints/GRV-2026-000001"))
                .andExpect(flash().attributeExists("flashError"));
        assertLog(Level.INFO, "REFUSED code=" + code);
    }

    @Test
    @DisplayName("TC-VE-14 a missing MVC resource logs only the request path and error type")
    void missingResource() throws Exception {
        mvc.perform(get("/images/missing.png"))
                .andExpect(status().isNotFound()).andExpect(view().name("error/404"));
        assertLog(Level.DEBUG, "CLIENT_ERROR status=404 type=NoResourceFoundException path=/images/missing.png");
    }

    @Test
    @DisplayName("TC-VE-14 validation without a BindingResult logs a warning and renders a 400")
    void unhandledBinding() throws Exception {
        mvc.perform(post("/test-only/form-unhandled").with(user("test")).with(csrf()).param("mobile", "123"))
                .andExpect(status().isBadRequest()).andExpect(view().name("error/4xx"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("123"))));
        assertLog(Level.WARN, "BIND_WITHOUT_BINDING_RESULT path=/test-only/form-unhandled");
    }

    @Test
    @DisplayName("TC-VE-14 a recognised unique constraint produces a field error without leaking JDBC values")
    void knownConstraintFieldError() throws Exception {
        mvc.perform(post("/test-only/form-unique").with(user("test")).with(csrf()))
                .andExpect(status().isOk()).andExpect(content().string("mobile=Mobile already registered."));
        assertLog(Level.INFO, "UNIQUE_RACE constraint=uq_app_user_mobile");
        assertThat(logs.entries()).noneMatch(entry -> entry.getFormattedMessage().contains(ErrorTriggerController.PRIVATE_MESSAGE));
    }

    @Test
    @DisplayName("TC-VE-14 security exceptions reach the Spring Security filter")
    void securityHandlesSecurityExceptions() throws Exception {
        mvc.perform(get("/test-only/errors/access").with(user("test")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/test-only/errors/authentication").with(user("test")))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("TC-VE-14 invalid request parameters, parts, and messages use the shared 400 view")
    void frameworkClientErrors() throws Exception {
        Map<String, AbstractMockHttpServletRequestBuilder<?>> requests = Map.of(
                "MissingServletRequestParameterException", get("/test-only/typed"),
                "MissingServletRequestPartException", multipart("/test-only/multipart").with(csrf()),
                "HandlerMethodValidationException", get("/test-only/positive").param("value", "0"),
                "HttpMessageNotReadableException", post("/test-only/json").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"number\":\"text\"}"));
        for (var entry : requests.entrySet()) {
            mvc.perform(entry.getValue().with(user("test")))
                    .andExpect(status().isBadRequest()).andExpect(view().name("error/4xx"))
                    .andExpect(model().attribute("titleKey", "page.error.400.title"));
            assertLog(Level.DEBUG, "CLIENT_ERROR status=400 type=" + entry.getKey());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"async-client-gone", "client-abort"})
    @DisplayName("TC-VE-14 a disconnected client receives no additional body")
    void clientDisconnected(String failure) throws Exception {
        mvc.perform(get("/test-only/errors/{failure}", failure).with(user("test")))
                .andExpect(content().string(""));
        assertLog(Level.DEBUG, "CLIENT_ERROR status=200 type=");
    }

    @Test
    @DisplayName("TC-VE-11 bad form fields and request parameter types are handled")
    void bindingErrors() throws Exception {
        mvc.perform(post("/test-only/form").with(user("test")).with(csrf())
                        .param("slaDays", "abc").param("departmentId", "abc"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("slaDays=")))
                .andExpect(content().string(containsString("SLA days must be a whole number from 1 to 90.")))
                .andExpect(content().string(containsString("departmentId=Choose a value from the list.")));
        mvc.perform(get("/test-only/typed").with(user("test")).param("id", "abc"))
                .andExpect(status().isBadRequest()).andExpect(model().attribute("titleKey", "page.error.400.title"));
        assertLog(Level.DEBUG, "CLIENT_ERROR status=400 type=MethodArgumentTypeMismatchException");
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
        assertLog(Level.DEBUG, "CLIENT_ERROR status=405 type=HttpRequestMethodNotSupportedException");
    }

    private void assertLog(Level level, String prefix) {
        assertThat(logs.entries()).anySatisfy(entry -> {
            assertThat(entry.getLevel()).isEqualTo(level);
            assertThat(entry.getFormattedMessage()).startsWith(prefix);
        });
    }
}
