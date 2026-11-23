package com.smartgrievance.auth;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import com.smartgrievance.otp.OtpStatusView;
import com.smartgrievance.security.LoginAttemptService;
import com.smartgrievance.seed.DemoData;
import com.smartgrievance.testsupport.Html;
import com.smartgrievance.testsupport.Sessions;
import com.smartgrievance.testsupport.WebTestBase;
import com.smartgrievance.user.AccountBootstrap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthenticationRouteWebTest extends WebTestBase {
    private static final AtomicInteger NETWORK_OFFSET = new AtomicInteger();
    private static final String CITIZEN = "mahesh@example.com";
    private static final String UNVERIFIED = "rahul@example.com";
    private static final String DEMO_PASSWORD = "Demo@1234";

    @Autowired AccountBootstrap bootstrap;
    @Autowired DemoData demo;
    @Autowired JdbcClient jdbc;
    @Autowired LoginAttemptService attempts;
    private int clientNumber;

    @BeforeEach
    void users() {
        bootstrap.ensureAdmin(properties.admin(), false);
        demo.seedUsers();
        clientNumber = NETWORK_OFFSET.incrementAndGet();
        for (String email : new String[]{CITIZEN, UNVERIFIED, "anita.deshmukh@example.com",
                "admin@example.com", "kiran.more@example.com"}) {
            attempts.clearAccount(email);
        }
    }

    @ParameterizedTest
    @CsvSource({"mahesh@example.com,Demo@1234,/citizen/complaints",
                "anita.deshmukh@example.com,Demo@1234,/officer/queue",
                "admin@example.com,Admin@1234,/admin/dashboard"})
    @DisplayName("TC-UC-02a users sign in with uppercase emails, land on their home, and sign out")
    void signInAndOut(String email, String password, String home) throws Exception {
        var session = login(email.toUpperCase(Locale.ROOT), password, home);
        mvc.perform(get(home).with(Sessions.requestedSession(session))).andExpect(status().isOk());
        mvc.perform(post("/logout").with(Sessions.requestedSession(session)).with(csrf()))
                .andExpect(redirectedUrl("/login?logout"));
        mvc.perform(get("/login").queryParam("logout", ""))
                .andExpect(content().string(containsString("You have been logged out.")));
    }

    @ParameterizedTest
    @CsvSource({"mahesh@example.com,Wrong!5231", "kiran.more@example.com,Demo@1234", "missing@example.test,Demo@1234"})
    @DisplayName("TC-UC-02b visitors get the same login message for inactive, unknown, or wrong credentials")
    void equivalentLoginOutcome(String email, String password) throws Exception {
        var failed = mvc.perform(loginForm(email, password)).andExpect(redirectedUrl("/login?error")).andReturn();
        mvc.perform(get("/login").queryParam("error", "").with(Sessions.requestedSession(session(failed))))
                .andExpect(content().string(containsString("Invalid email or password, or account inactive.")));
    }

    @Test
    @DisplayName("TC-UC-02c an expired browser session sends both reading and form submission back to login")
    void expiredBrowserSession() throws Exception {
        mvc.perform(get("/citizen/complaints").with(Sessions.invalidSession()))
                .andExpect(redirectedUrl("/login?expired"));
        mvc.perform(post("/account/password").with(Sessions.invalidSession()))
                .andExpect(redirectedUrl("/login?expired"));
        mvc.perform(get("/login").queryParam("expired", ""))
                .andExpect(content().string(containsString("Your session has ended")));
    }

    @Test
    @DisplayName("TC-RT-03 login enforces CSRF and throttling in a browser session")
    void loginContract() throws Exception {
        var page = mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        mvc.perform(post("/login").with(Sessions.requestedSession(session(page)))
                        .param("email", CITIZEN).param("password", DEMO_PASSWORD))
                .andExpect(status().isForbidden());
        String email = "route-login-" + clientNumber + "@example.test";
        for (int failure = 0; failure < 5; failure++) {
            mvc.perform(loginForm(email, "Wrong!5231")).andExpect(redirectedUrl("/login?error"));
        }
        mvc.perform(loginForm(email, "Wrong!5231")).andExpect(redirectedUrl("/login?locked"));
    }

    @Test
    @DisplayName("TC-RT-04 logout does not accept GET and POST expires the JSESSIONID cookie")
    void logoutContract() throws Exception {
        var session = login(CITIZEN, DEMO_PASSWORD, "/citizen/complaints");
        mvc.perform(get("/logout").with(Sessions.requestedSession(session))).andExpect(status().isNotFound());
        mvc.perform(post("/logout").with(Sessions.requestedSession(session)).with(csrf()))
                .andExpect(redirectedUrl("/login?logout")).andExpect(cookie().maxAge("JSESSIONID", 0));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("TC-RT-06 registration creates a citizen even when the form supplies a different role")
    void registrationContract() throws Exception {
        String email = "register-route-" + clientNumber + "@example.test";
        var created = mvc.perform(post("/register").with(csrf()).with(this::client)
                        .param("fullName", "Priya Route").param("email", email).param("mobile", "9820000009")
                        .param("password", "Nacre!77").param("confirmPassword", "Nacre!77").param("role", "ADMIN"))
                .andExpect(redirectedUrl("/verify-mobile")).andExpect(flash().attributeExists("flashSuccess", "demoOtp"))
                .andReturn();
        assertThat(session(created)).isNotNull();
        assertThat(jdbc.sql("SELECT role FROM app_user WHERE email = :email")
                .param("email", email).query(String.class).single()).isEqualTo("CITIZEN");
    }

    @ParameterizedTest
    @CsvSource({"mahesh@example.com,Demo@1234,/citizen/complaints",
                "anita.deshmukh@example.com,Demo@1234,/officer/queue",
                "admin@example.com,Admin@1234,/admin/dashboard"})
    @DisplayName("TC-RT-07 the account password page is available to all three roles")
    void passwordForm(String email, String password, String home) throws Exception {
        mvc.perform(get("/account/password").with(Sessions.requestedSession(login(email, password, home))))
                .andExpect(status().isOk()).andExpect(view().name("auth/change-password"))
                .andExpect(model().attributeExists("form"));
    }

    @ParameterizedTest
    @CsvSource({"wrong,NewPassword77,NewPassword77,currentPassword,Current password is incorrect.",
                "Demo@1234,Demo@1234,Demo@1234,newPassword,The new password must be different from the current password.",
                "Demo@1234,short1,short1,newPassword,Password must be at least 8 characters.",
                "Demo@1234,NewPassword77,OtherPassword77,confirmPassword,The passwords do not match."})
    @DisplayName("TC-RT-08 invalid password changes return field errors without rotating the valid session")
    void rejectedPasswordChange(String current, String replacement, String confirm, String field, String text) throws Exception {
        var session = login(CITIZEN, DEMO_PASSWORD, "/citizen/complaints");
        String previousId = session.getId();
        mvc.perform(post("/account/password").with(Sessions.requestedSession(session)).with(csrf())
                        .param("currentPassword", current).param("newPassword", replacement).param("confirmPassword", confirm))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", field))
                .andExpect(content().string(containsString(text)));
        assertThat(session.getId()).isEqualTo(previousId);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("TC-UC-04c a previously correct OTP is refused after expiry or exhausted attempts")
    void expiredOrExhaustedOtp(boolean expire) throws Exception {
        var session = unverifiedSession();
        String code = issueOtp(session);
        if (expire) {
            testClock.advance(Duration.ofMinutes(5).plusSeconds(1));
        } else {
            for (int attempt = 0; attempt < 3; attempt++) {
                mvc.perform(verifyForm(session, wrong(code))).andExpect(status().isOk());
            }
        }
        mvc.perform(verifyForm(session, code)).andExpect(status().isOk())
                .andExpect(content().string(containsString("OTP expired, please request a new one.")));
    }

    @Test
    @DisplayName("TC-UC-04e an unverified user can browse complaints and is redirected before starting one")
    void skipVerification() throws Exception {
        var session = unverifiedSession();
        mvc.perform(get("/citizen/complaints").with(Sessions.requestedSession(session)))
                .andExpect(content().string(containsString("Verify your mobile number to file complaints.")));
        mvc.perform(get("/citizen/complaints/new").with(Sessions.requestedSession(session)))
                .andExpect(redirectedUrl("/verify-mobile"))
                .andExpect(flash().attribute("flashInfo", "Verify your mobile number to file complaints."));
    }

    @Test
    @DisplayName("TC-RT-11 OTP validation distinguishes invalid input, a wrong code, and a successful verification")
    void verifyContract() throws Exception {
        var session = unverifiedSession();
        String code = issueOtp(session);
        mvc.perform(verifyForm(session, "")).andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "code"))
                .andExpect(content().string(containsString("Enter the 6-digit OTP.")));
        mvc.perform(verifyForm(session, "12345")).andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "code"))
                .andExpect(content().string(containsString("The OTP is 6 digits.")));
        mvc.perform(verifyForm(session, wrong(code))).andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("form", "code", "validation.code.incorrect"));
        mvc.perform(verifyForm(session, code)).andExpect(redirectedUrl("/citizen/complaints"))
                .andExpect(flash().attributeExists("flashSuccess"));
        assertThat(jdbc.sql("SELECT mobile_verified FROM app_user WHERE email = :email")
                .param("email", UNVERIFIED).query(Boolean.class).single()).isTrue();
    }

    @Test
    @DisplayName("TC-RT-12 resend returns the masked recipient and offers the newly issued demo code")
    void resendContract() throws Exception {
        var session = unverifiedSession();
        issueOtp(session);
        testClock.advance(Duration.ofMinutes(1));
        mvc.perform(post("/verify-mobile/resend").with(Sessions.requestedSession(session)).with(csrf()))
                .andExpect(redirectedUrl("/verify-mobile"))
                .andExpect(flash().attribute("flashSuccess", "A new OTP was sent to ******0004."))
                .andExpect(flash().attributeExists("demoOtp"));
    }

    @Test
    @DisplayName("TC-VE-23 a refusal message appears only on the next visit and validation stays in the form")
    void flashDisplaysOnce() throws Exception {
        var session = unverifiedSession();
        issueOtp(session);
        mvc.perform(post("/verify-mobile/resend").with(Sessions.requestedSession(session)).with(csrf()))
                .andExpect(redirectedUrl("/verify-mobile")).andExpect(flash().attributeExists("flashError"));
        var first = get("/verify-mobile").with(Sessions.requestedSession(session));
        assertThat(Html.of(mvc.perform(first).andReturn()).text("main")).contains("Please wait");
        var next = get("/verify-mobile").with(Sessions.requestedSession(session));
        assertThat(Html.of(mvc.perform(next).andReturn()).text("main")).doesNotContain("Please wait");
        mvc.perform(verifyForm(session, "abc")).andExpect(status().isOk()).andExpect(flash().attributeCount(0));
    }

    @Test
    @DisplayName("TC-VE-25 account form error links point to unique field IDs")
    void errorSummaryTargets() throws Exception {
        var registration = mvc.perform(post("/register").with(csrf()).with(this::client)).andReturn();
        var citizen = login(CITIZEN, DEMO_PASSWORD, "/citizen/complaints");
        var password = mvc.perform(post("/account/password").with(Sessions.requestedSession(citizen)).with(csrf())).andReturn();
        var verification = mvc.perform(verifyForm(unverifiedSession(), "abc")).andReturn();
        for (var result : new MvcResult[]{registration, password, verification}) {
            var html = Html.of(result);
            assertThat(html.ids()).doesNotHaveDuplicates();
            var links = html.document().select("#error-summary a").eachAttr("href");
            assertThat(links).isNotEmpty().allSatisfy(link -> assertThat(html.document().select(link)).hasSize(1));
        }
    }

    private MockHttpSession unverifiedSession() throws Exception {
        return login(UNVERIFIED, DEMO_PASSWORD, "/citizen/complaints");
    }

    private String issueOtp(MockHttpSession session) throws Exception {
        var result = mvc.perform(get("/verify-mobile").with(Sessions.requestedSession(session)))
                .andExpect(status().isOk()).andReturn();
        return ((OtpStatusView) result.getModelAndView().getModel().get("otp")).demoCode();
    }

    private MockHttpSession login(String email, String password, String home) throws Exception {
        return session(mvc.perform(loginForm(email, password)).andExpect(redirectedUrl(home)).andReturn());
    }

    private MockHttpServletRequestBuilder loginForm(String email, String password) {
        return post("/login").with(csrf()).with(this::client).param("email", email).param("password", password);
    }

    private MockHttpServletRequestBuilder verifyForm(MockHttpSession session, String code) {
        return post("/verify-mobile").with(Sessions.requestedSession(session)).with(csrf()).param("code", code);
    }

    private MockHttpServletRequest client(MockHttpServletRequest request) {
        request.setRemoteAddr("198.51.100." + clientNumber);
        return request;
    }

    private static MockHttpSession session(MvcResult result) {
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String wrong(String code) { return code.equals("000000") ? "000001" : "000000"; }
}
