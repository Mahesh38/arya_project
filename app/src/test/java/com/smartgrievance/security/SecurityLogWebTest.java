package com.smartgrievance.security;

import static com.smartgrievance.testsupport.Sessions.requestedSession;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import com.smartgrievance.seed.DemoData;
import com.smartgrievance.testsupport.LogCapture;
import com.smartgrievance.testsupport.WebTestBase;
import com.smartgrievance.user.AccountBootstrap;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Doc 11 §10 and doc 12 §9: one realistic run through login, registration, OTP, password change and a rejected form,
 * then every captured log event (message, MDC, stack trace) is checked for secrets and personal data.
 */
class SecurityLogWebTest extends WebTestBase {
    private static final String WRONG_PASSWORD = "Wrong#Secret771";
    private static final String NEW_PASSWORD = "Strong#Log5571";
    private static final String CHANGED_PASSWORD = "Newer#Log88240";
    private static final String BAD_EMAIL = "rejected..value@@nowhere";
    @Autowired AccountBootstrap bootstrap;
    @Autowired DemoData users;
    @RegisterExtension final LogCapture logs = new LogCapture();

    @BeforeEach
    void seed() {
        bootstrap.ensureAdmin(properties.admin(), false);
        users.seedUsers();
    }

    @Test
    @DisplayName("TC-SEC-54, TC-SEC-55, TC-VE-32 security events are logged masked and no password, OTP or session id leaks")
    void logsAreMaskedAndFreeOfSecrets() throws Exception {
        var sessionIds = new ArrayList<String>();
        mvc.perform(post("/login").with(csrf()).with(ip()).param("email", "mahesh@example.com").param("password", WRONG_PASSWORD))
                .andExpect(redirectedUrl("/login?error"));

        mvc.perform(post("/register").with(csrf()).with(ip()).param("fullName", "Log Check").param("email", BAD_EMAIL)
                .param("mobile", "98200 00077").param("password", NEW_PASSWORD).param("confirmPassword", NEW_PASSWORD))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "email"));

        var registered = mvc.perform(post("/register").with(csrf()).with(ip()).param("fullName", "Log Check")
                        .param("email", "log.check@example.com").param("mobile", "98200 00077")
                        .param("password", NEW_PASSWORD).param("confirmPassword", NEW_PASSWORD))
                .andExpect(redirectedUrl("/verify-mobile")).andReturn();
        var session = session(registered, sessionIds);
        String code = (String) registered.getFlashMap().get("demoOtp");
        assertThat(code).matches("[0-9]{6}");
        String wrongCode = code.equals("000000") ? "111111" : "000000";
        mvc.perform(post("/verify-mobile").with(requestedSession(session)).with(csrf()).param("code", wrongCode));
        var verified = mvc.perform(post("/verify-mobile").with(requestedSession(session)).with(csrf()).param("code", code))
                .andExpect(redirectedUrl("/citizen/complaints")).andReturn();
        session = session(verified, sessionIds);
        var changed = mvc.perform(post("/account/password").with(requestedSession(session)).with(csrf())
                .param("currentPassword", NEW_PASSWORD).param("newPassword", CHANGED_PASSWORD)
                .param("confirmPassword", CHANGED_PASSWORD)).andReturn();
        session = session(changed, sessionIds);
        mvc.perform(get("/citizen/complaints").with(requestedSession(session))).andExpect(status().isOk());

        var events = logs.entries();
        var names = events.stream().filter(event -> event.getLoggerName().equals("SECURITY"))
                .map(event -> event.getFormattedMessage().split(" ")[0]).toList();
        assertThat(names).contains("LOGIN_FAILURE", "REGISTERED", "OTP_ISSUED", "DEMO-OTP", "OTP_FAILED", "OTP_VERIFIED",
                "PASSWORD_CHANGED");
        assertThat(lines(events, "LOGIN_FAILURE")).allSatisfy(line ->
                assertThat(line).contains("email=m***@example.com").doesNotContain("mahesh@example.com"));
        assertThat(lines(events, "REGISTERED")).allSatisfy(line -> assertThat(line).doesNotContain("log.check@example.com"));

        for (var event : events) {
            String text = everything(event);
            assertThat(text).as(event.getFormattedMessage())
                    .doesNotContain(WRONG_PASSWORD, NEW_PASSWORD, CHANGED_PASSWORD, BAD_EMAIL, wrongCode, "9820000077");
            assertThat(text).doesNotContainIgnoringCase("JSESSIONID").doesNotContainIgnoringCase("sessionId");
            // MockMvc session ids are short counters; real Tomcat ids are 32 hex characters
            for (String id : sessionIds) if (id.length() >= 16) assertThat(text).doesNotContain(id);
            if (!event.getFormattedMessage().startsWith("DEMO-OTP")) assertThat(text).doesNotContain(code);
        }
    }

    private static List<String> lines(List<ILoggingEvent> events, String name) {
        return events.stream().map(ILoggingEvent::getFormattedMessage).filter(line -> line.startsWith(name)).toList();
    }

    private static String everything(ILoggingEvent event) {
        var text = new StringBuilder(event.getFormattedMessage()).append(' ').append(event.getMDCPropertyMap());
        if (event.getThrowableProxy() != null) text.append(ThrowableProxyUtil.asString(event.getThrowableProxy()));
        return text.toString();
    }

    private static MockHttpSession session(MvcResult result, List<String> ids) {
        var session = (MockHttpSession) result.getRequest().getSession(false);
        ids.add(session.getId());
        return session;
    }

    private static RequestPostProcessor ip() {
        return request -> {
            request.setRemoteAddr("192.0.2.211");
            return request;
        };
    }
}
