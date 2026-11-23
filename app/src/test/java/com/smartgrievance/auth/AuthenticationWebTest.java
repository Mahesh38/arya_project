package com.smartgrievance.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static com.smartgrievance.testsupport.Sessions.invalidSession;
import static com.smartgrievance.testsupport.Sessions.requestedSession;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import com.smartgrievance.seed.DemoData;
import com.smartgrievance.testsupport.WebTestBase;
import com.smartgrievance.user.AccountBootstrap;
import com.smartgrievance.user.CurrentAccountService;
import com.smartgrievance.user.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.jsoup.Jsoup;

class AuthenticationWebTest extends WebTestBase {
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);
    @Autowired private DemoData demo;
    @Autowired private AccountBootstrap bootstrap;
    @Autowired private JdbcClient jdbc;
    @Autowired private CurrentAccountService accounts;
    private String clientIp;

    @BeforeEach
    void seedUsers() {
        bootstrap.ensureAdmin(properties.admin(), false);
        demo.seedUsers();
        clientIp = "192.0.2." + NEXT_IP.getAndIncrement();
    }

    @Test
    @DisplayName("TC-SEC-01 visitor redirects to the login page; polling returns JSON without a session")
    void anonymous() throws Exception {
        mvc.perform(get("/citizen/complaints")).andExpect(redirectedUrl("/login"));
        var count = mvc.perform(get("/notifications/unread-count")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthenticated")).andReturn();
        assertThat(count.getRequest().getSession(false)).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "MAHESH@EXAMPLE.COM,Demo@1234,/citizen/complaints",
            "ANITA.DESHMUKH@EXAMPLE.COM,Demo@1234,/officer/queue",
            "ADMIN@EXAMPLE.COM,Admin@1234,/admin/dashboard"})
    @DisplayName("TC-SEC-04 login normalizes email and sends each role to its home")
    void roleLanding(String email, String password, String expected) throws Exception {
        var result = mvc.perform(login(email, password)).andExpect(redirectedUrl(expected)).andReturn();
        var session = session(result);
        assertThat(user(session).getPassword()).isNull();
        mvc.perform(get(expected).with(requestedSession(session))).andExpect(status().isOk()).andExpect(view().name("auth/role-home"));
        mvc.perform(get("/login").with(requestedSession(session))).andExpect(redirectedUrl("/"));
        mvc.perform(get("/register").with(requestedSession(session))).andExpect(redirectedUrl("/"));
        mvc.perform(get("/").with(requestedSession(session))).andExpect(redirectedUrl(expected));
        mvc.perform(get("/account/password").with(requestedSession(session)))
                .andExpect(status().isOk()).andExpect(view().name("auth/change-password"));
    }

    @Test
    @DisplayName("TC-SEC-05 a failed login keeps the normalized email for one display")
    void emailOnce() throws Exception {
        var failed = mvc.perform(login("  MAHESH@EXAMPLE.COM  ", "Wrong1pass"))
                .andExpect(redirectedUrl("/login?error")).andReturn();
        var session = session(failed);
        mvc.perform(get("/login?error").with(requestedSession(session)))
                .andExpect(model().attribute("form", new LoginController.LoginForm("mahesh@example.com")));
        mvc.perform(get("/login").with(requestedSession(session)))
                .andExpect(model().attribute("form", new LoginController.LoginForm("")));
    }

    @ParameterizedTest
    @CsvSource({"kiran.more@example.com,Demo@1234", "unknown@example.com,Demo@1234", "mahesh@example.com,Wrong1pass"})
    @DisplayName("TC-SEC-06 inactive, unknown and wrong credentials get the same response")
    void genericFailure(String email, String password) throws Exception {
        mvc.perform(login(email, password)).andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @DisplayName("TC-SEC-07 five failures lock an account, and time releases it")
    void lockout() throws Exception {
        for (int i = 0; i < properties.security().login().maxFailuresPerAccount(); i++) {
            mvc.perform(login("rahul@example.com", "Wrong1pass")).andExpect(redirectedUrl("/login?error"));
        }
        mvc.perform(login("rahul@example.com", "Demo@1234")).andExpect(redirectedUrl("/login?locked"));
        testClock.advance(properties.security().login().lock());
        mvc.perform(login("rahul@example.com", "Demo@1234")).andExpect(redirectedUrl("/citizen/complaints"));
    }

    @Test
    @DisplayName("TC-SEC-08 too many failed emails lock one IP")
    void ipLimit() throws Exception {
        for (int i = 0; i < properties.security().login().maxFailuresPerIp(); i++) {
            mvc.perform(login("unknown" + i + "@example.com", "Wrong1pass")).andExpect(redirectedUrl("/login?error"));
        }
        mvc.perform(login("mahesh@example.com", "Demo@1234")).andExpect(redirectedUrl("/login?locked"));
    }

    @Test
    @DisplayName("TC-SEC-10 authenticated submissions require a valid CSRF token")
    void tokenRequired() throws Exception {
        var session = session(mvc.perform(login("rahul@example.com", "Demo@1234")).andReturn());
        mvc.perform(post("/verify-mobile").with(requestedSession(session)).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/verify-mobile")).andExpect(redirectedUrl("/login"));
        var guest = session(mvc.perform(get("/login")).andReturn());
        mvc.perform(post("/login").with(requestedSession(guest))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-SEC-15 logout requires POST and removes the authenticated session")
    void logout() throws Exception {
        var session = session(mvc.perform(login("mahesh@example.com", "Demo@1234")).andReturn());
        mvc.perform(get("/logout").with(requestedSession(session))).andExpect(status().isNotFound());
        mvc.perform(post("/logout").with(requestedSession(session)).with(csrf()))
                .andExpect(redirectedUrl("/login?logout")).andExpect(cookie().maxAge("JSESSIONID", 0));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("TC-SEC-26 deactivation ends the next authenticated request")
    void inactiveSession() throws Exception {
        var session = session(mvc.perform(login("mahesh@example.com", "Demo@1234")).andReturn());
        jdbc.sql("UPDATE app_user SET active = FALSE, version = version + 1 WHERE id = :id")
                .param("id", user(session).id()).update();
        mvc.perform(get("/citizen/complaints").with(requestedSession(session))).andExpect(redirectedUrl("/login?inactive"));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("TC-UC-03a password change continues the current session and ends other sessions")
    void changePassword() throws Exception {
        var current = session(mvc.perform(login("mahesh@example.com", "Demo@1234")).andReturn());
        var previous = session(mvc.perform(login("mahesh@example.com", "Demo@1234")).andReturn());
        var id = current.getId();
        var changed = mvc.perform(post("/account/password").with(requestedSession(current)).with(csrf())
                .param("currentPassword", "Demo@1234").param("newPassword", "Unique@2345987")
                .param("confirmPassword", "Unique@2345987"))
                .andExpect(redirectedUrl("/account/password")).andReturn();
        assertThat(session(changed).getId()).isNotEqualTo(id);
        assertThat(accounts.find(user(session(changed)).id()).orElseThrow().version()).isEqualTo(1);
        mvc.perform(get("/citizen/complaints").with(requestedSession(previous))).andExpect(redirectedUrl("/login?expired"));
        mvc.perform(get("/account/password").with(requestedSession(session(changed)))).andExpect(status().isOk());
        mvc.perform(login("mahesh@example.com", "Unique@2345987")).andExpect(redirectedUrl("/citizen/complaints"));
    }

    @Test
    @DisplayName("TC-UC-01a registration persists the normalized account and signs the citizen in")
    void registration() throws Exception {
        var before = mvc.perform(get("/register")).andExpect(status().isOk()).andReturn();
        MockHttpSession anonymous = session(before);
        var id = anonymous.getId();
        var registered = mvc.perform(register("Priya@Example.com", "98200 00009", "Strong@123", "Strong@123")
                .with(requestedSession(anonymous)).param("role", "ADMIN"))
                .andExpect(redirectedUrl("/verify-mobile")).andExpect(flash().attributeExists("demoOtp")).andReturn();
        var user = user(session(registered));
        var row = accounts.find(user.id()).orElseThrow();
        assertThat(session(registered).getId()).isNotEqualTo(id);
        assertThat(row.email()).isEqualTo("priya@example.com");
        assertThat(row.mobile()).isEqualTo("9820000009");
        assertThat(row.passwordHash()).startsWith("$2a$04$").doesNotContain("Strong@123");
        assertThat(row.mobileVerified()).isFalse();
        assertThat(row.role().name()).isEqualTo("CITIZEN");
        mvc.perform(get("/verify-mobile").with(requestedSession(session(registered))))
                .andExpect(status().isOk()).andExpect(view().name("auth/verify-mobile"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Account created.")));
        mvc.perform(get("/verify-mobile").with(requestedSession(session(registered))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Account created."))));
    }

    @Test
    @DisplayName("TC-UC-01b registered email and mobile get field errors")
    void duplicate() throws Exception {
        mvc.perform(register("MAHESH@example.com", "9820000009", "Strong@123", "Strong@123"))
                .andExpect(model().attributeHasFieldErrorCode("form", "email", "validation.email.taken"));
        mvc.perform(register("priya@example.com", "9800000001", "Strong@123", "Strong@123"))
                .andExpect(model().attributeHasFieldErrorCode("form", "mobile", "validation.mobile.taken"));
    }

    @ParameterizedTest
    @CsvSource({"Strong@123,Other@123,confirmPassword,The passwords do not match.",
            "password1,password1,password,This password is too common. Choose one that is harder to guess.",
            "short1,short1,password,Password must be at least 8 characters."})
    @DisplayName("TC-UC-01c invalid or mismatching passwords are rejected without echoing them")
    void invalidPassword(String password, String confirmation, String field, String message) throws Exception {
        mvc.perform(register("priya@example.com", "9820000009", password, confirmation))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", field))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(message)))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("value=\"" + password + "\""))));
    }

    @Test
    @DisplayName("TC-VE-31 registration uses English messages regardless of the Accept-Language header")
    void registrationLocale() throws Exception {
        mvc.perform(post("/register").with(csrf()).with(ip()).header("Accept-Language", "de-DE,hi-IN"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Enter the full name.")));
    }

    @Test
    @DisplayName("TC-UC-04a a citizen verifies the issued code without logging in again")
    void verifyMobile() throws Exception {
        var session = session(mvc.perform(login("rahul@example.com", "Demo@1234")).andReturn());
        mvc.perform(get("/citizen/complaints").with(requestedSession(session)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Verify your mobile number to file complaints")));
        mvc.perform(get("/citizen/complaints/new").with(requestedSession(session)))
                .andExpect(redirectedUrl("/verify-mobile"));
        var response = mvc.perform(get("/verify-mobile").with(requestedSession(session))).andExpect(status().isOk()).andReturn();
        var otp = (com.smartgrievance.otp.OtpStatusView) response.getModelAndView().getModel().get("otp");
        assertThat(otp.demoCode()).matches("[0-9]{6}");
        mvc.perform(post("/verify-mobile").with(requestedSession(session)).with(csrf()).param("code", otp.demoCode()))
                .andExpect(redirectedUrl("/citizen/complaints"));
        assertThat(user(session).mobileVerified()).isTrue();
        mvc.perform(get("/verify-mobile").with(requestedSession(session))).andExpect(redirectedUrl("/citizen/complaints"));
    }

    @Test
    @DisplayName("TC-UC-04d resend respects the cooldown and issues a new code later")
    void otpCooldown() throws Exception {
        var session = session(mvc.perform(login("rahul@example.com", "Demo@1234")).andReturn());
        mvc.perform(get("/verify-mobile").with(requestedSession(session))).andExpect(status().isOk());
        testClock.advance(Duration.ofSeconds(10));
        mvc.perform(post("/verify-mobile/resend").with(requestedSession(session)).with(csrf()))
                .andExpect(flash().attribute("flashError", "Please wait 50 seconds before requesting a new OTP."));
        testClock.advance(Duration.ofMinutes(1));
        mvc.perform(post("/verify-mobile/resend").with(requestedSession(session)).with(csrf()))
                .andExpect(redirectedUrl("/verify-mobile")).andExpect(flash().attributeExists("demoOtp"));
    }

    @Test
    @DisplayName("TC-SEC-12 password login rotates a previously established session ID")
    void loginRotatesSession() throws Exception {
        var before = session(mvc.perform(get("/login")).andExpect(status().isOk()).andReturn());
        var original = before.getId();
        mvc.perform(login("mahesh@example.com", "Demo@1234").with(requestedSession(before)))
                .andExpect(redirectedUrl("/citizen/complaints"));
        assertThat(before.getId()).isNotEqualTo(original);
        assertThat(user(before).getPassword()).isNull();
    }

    @Test
    @DisplayName("TC-SEC-14 stale session IDs redirect page requests and invalid form submissions to expiry")
    void expiredSession() throws Exception {
        mvc.perform(get("/citizen/complaints").with(invalidSession())).andExpect(redirectedUrl("/login?expired"));
        mvc.perform(post("/account/password").with(invalidSession())).andExpect(redirectedUrl("/login?expired"));
        mvc.perform(get("/notifications/unread-count").with(invalidSession())).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-SEC-34 only an allowed saved request can override the role home")
    void savedTarget() throws Exception {
        var initial = mvc.perform(get("/account/password").queryParam("display", "details"))
                .andExpect(redirectedUrl("/login")).andReturn();
        mvc.perform(login("mahesh@example.com", "Demo@1234").with(requestedSession(session(initial))))
                .andExpect(redirectedUrl("/account/password?display=details&continue"));
        var otherRole = mvc.perform(get("/admin/dashboard")).andExpect(redirectedUrl("/login")).andReturn();
        mvc.perform(login("mahesh@example.com", "Demo@1234")
                        .with(requestedSession(session(otherRole))).param("returnUrl", "/admin/dashboard"))
                .andExpect(redirectedUrl("/citizen/complaints"));
    }

    @Test
    @DisplayName("TC-SEC-27 role requests refresh stale department information")
    void latestDepartment() throws Exception {
        var session = session(mvc.perform(login("anita.deshmukh@example.com", "Demo@1234")).andReturn());
        var userId = user(session).id();
        jdbc.sql("""
                UPDATE app_user SET department_id = (SELECT id FROM department WHERE name = 'Drainage'),
                    version = version + 1 WHERE id = :id
                """).param("id", userId).update();
        mvc.perform(get("/officer/queue").with(requestedSession(session))).andExpect(status().isOk());
        assertThat(user(session).departmentName()).isEqualTo("Drainage");
        assertThat(user(session).version()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-SEC-18 a role mismatch cannot write OTP state")
    void verifyRoleRestrictions() throws Exception {
        for (String email : new String[]{"admin@example.com", "anita.deshmukh@example.com"}) {
            var password = email.startsWith("admin") ? "Admin@1234" : "Demo@1234";
            var session = session(mvc.perform(login(email, password)).andReturn());
            mvc.perform(get("/verify-mobile").with(requestedSession(session))).andExpect(status().isForbidden());
            mvc.perform(post("/verify-mobile").with(requestedSession(session)).with(csrf()).param("code", "123456"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/verify-mobile/resend").with(requestedSession(session)).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        assertThat(jdbc.sql("SELECT COUNT(*) FROM mobile_otp").query(Integer.class).single()).isZero();
    }

    @Test
    @DisplayName("TC-SEC-09 the eleventh registration submission adds a global rate-limit message")
    void registrationLimit() throws Exception {
        for (int i = 0; i < properties.security().register().maxPerIpPerHour(); i++) {
            mvc.perform(post("/register").with(csrf()).with(ip()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Too many registrations"))));
        }
        mvc.perform(post("/register").with(csrf()).with(ip())).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Too many registrations from this network.")));
    }

    @Test
    @DisplayName("TC-SEC-36 three wrong code submissions exhaust an OTP")
    void attemptsExhausted() throws Exception {
        var session = session(mvc.perform(login("rahul@example.com", "Demo@1234")).andReturn());
        var response = mvc.perform(get("/verify-mobile").with(requestedSession(session))).andReturn();
        var otp = (com.smartgrievance.otp.OtpStatusView) response.getModelAndView().getModel().get("otp");
        String wrong = otp.demoCode().equals("000000") ? "000001" : "000000";
        for (int i = 0; i < properties.otp().maxAttempts(); i++) {
            mvc.perform(post("/verify-mobile").with(requestedSession(session)).with(csrf()).param("code", wrong))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/verify-mobile").with(requestedSession(session)).with(csrf()).param("code", otp.demoCode()))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("OTP expired")));
        assertThat(user(session).mobileVerified()).isFalse();
    }

    @Test
    @DisplayName("TC-BR-33 a code expires at the configured duration and issuance is limited per hour")
    void expiryAndHourlyLimit() throws Exception {
        var session = session(mvc.perform(login("rahul@example.com", "Demo@1234")).andReturn());
        var issued = mvc.perform(get("/verify-mobile").with(requestedSession(session))).andReturn();
        var otp = (com.smartgrievance.otp.OtpStatusView) issued.getModelAndView().getModel().get("otp");
        testClock.advance(properties.otp().ttl());
        mvc.perform(post("/verify-mobile").with(requestedSession(session)).with(csrf()).param("code", otp.demoCode()))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("OTP expired")));
        for (int i = 1; i < properties.otp().maxSendsPerHour(); i++) {
            mvc.perform(post("/verify-mobile/resend").with(requestedSession(session)).with(csrf()))
                    .andExpect(flash().attributeExists("demoOtp"));
            testClock.advance(properties.otp().resendCooldown());
        }
        mvc.perform(post("/verify-mobile/resend").with(requestedSession(session)).with(csrf()))
                .andExpect(flash().attribute("flashError", org.hamcrest.Matchers.startsWith("Too many OTP requests")));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM mobile_otp WHERE used = FALSE").query(Integer.class).single()).isOne();
    }

    @Test
    @DisplayName("TC-VE-24 registration errors preserve field order, accessible links, and unique IDs")
    void errorSummary() throws Exception {
        var invalid = mvc.perform(post("/register").with(csrf()).with(ip())).andExpect(status().isOk()).andReturn();
        var document = Jsoup.parse(invalid.getResponse().getContentAsString());
        assertThat(document.select("#error-summary a").eachAttr("href")).containsExactly(
                "#fullName", "#email", "#mobile", "#password", "#confirmPassword");
        assertThat(document.select("[id]").eachAttr("id")).doesNotHaveDuplicates();
        assertThat(document.selectFirst("#email").attr("aria-invalid")).isEqualTo("true");
    }

    @Test
    @DisplayName("TC-UT-16 bootstrap is idempotent and preserves an existing inactive administrator")
    void bootstrapIsIdempotent() {
        int admins = jdbc.sql("SELECT COUNT(*) FROM app_user WHERE role = 'ADMIN'").query(Integer.class).single();
        assertThat(admins).isOne();
        jdbc.sql("UPDATE app_user SET active = FALSE, version = version + 1 WHERE role = 'ADMIN'").update();
        bootstrap.ensureAdmin(properties.admin(), false);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM app_user WHERE role = 'ADMIN' AND active = FALSE")
                .query(Integer.class).single()).isOne();
    }

    @Test
    @DisplayName("TC-RT-01 the anonymous home route redirects to login")
    void anonymousHome() throws Exception {
        mvc.perform(get("/")).andExpect(redirectedUrl("/login"));
    }

    @ParameterizedTest
    @CsvSource({
            "error,Invalid email or password",
            "locked,Too many failed attempts",
            "logout,You have been logged out",
            "expired,Your session has ended",
            "inactive,Your account has been deactivated"})
    @DisplayName("TC-RT-02 the login screen displays each safe problem flag")
    void loginFlags(String flag, String message) throws Exception {
        mvc.perform(get("/login").queryParam(flag, "")).andExpect(status().isOk())
                .andExpect(view().name("auth/login"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(message)));
    }

    @Test
    @DisplayName("TC-RT-05 the public registration screen opens an empty form and contains the privacy notice")
    void registerPage() throws Exception {
        var page = mvc.perform(get("/register")).andExpect(status().isOk()).andExpect(view().name("auth/register"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Accounts are for adults"))).andReturn();
        var form = (RegistrationForm) page.getModelAndView().getModel().get("form");
        assertThat(form.getFullName()).isNull();
        assertThat(form.getEmail()).isNull();
        assertThat(form.getMobile()).isNull();
        assertThat(form.getPassword()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "fullName,X,Full name must be 2–100 characters.",
            "fullName,Priya9,Use letters",
            "email,invalid,Enter a valid email address",
            "mobile,5800000001,Enter a 10-digit mobile number",
            "mobile,98000000,Enter a 10-digit mobile number",
            "password,longletters,Password must contain at least one letter and one digit."
    })
    @DisplayName("TC-VE-01 registration displays the specified validation message for the field")
    void registrationMessages(String field, String input, String message) throws Exception {
        var request = post("/register").with(csrf()).with(ip())
                .param("fullName", field.equals("fullName") ? input : "Priya Nair")
                .param("email", field.equals("email") ? input : "priya@example.com")
                .param("mobile", field.equals("mobile") ? input : "9820000009")
                .param("password", field.equals("password") ? input : "Strong@123")
                .param("confirmPassword", field.equals("password") ? input : "Strong@123");
        var result = mvc.perform(request).andExpect(status().isOk()).andExpect(view().name("auth/register"))
                .andExpect(model().attributeHasFieldErrors("form", field)).andReturn();
        var document = Jsoup.parse(result.getResponse().getContentAsString());
        assertThat(document.select("#error-summary a[href='#" + field + "']").text()).contains(message);
    }

    @Test
    @DisplayName("TC-BR-28 registered email uniqueness crosses roles")
    void officerEmailReserved() throws Exception {
        mvc.perform(register("ANITA.deshmukh@example.com", "9820000009", "Strong@123", "Strong@123"))
                .andExpect(model().attributeHasFieldErrorCode("form", "email", "validation.email.taken"));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM app_user WHERE email <> LOWER(email)").query(Integer.class).single())
                .isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "Wrong1pass,Unique@2345987,currentPassword,Current password is incorrect.",
            "Demo@1234,Demo@1234,newPassword,The new password must be different from the current password."
    })
    @DisplayName("TC-UC-03b rebuffed password changes keep the current password and session")
    void rejectedPasswordChange(String current, String next, String field, String message) throws Exception {
        var session = session(mvc.perform(login("mahesh@example.com", "Demo@1234")).andReturn());
        var id = session.getId();
        mvc.perform(post("/account/password").with(requestedSession(session)).with(csrf())
                        .param("currentPassword", current).param("newPassword", next).param("confirmPassword", next))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", field))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(message)));
        assertThat(session.getId()).isEqualTo(id);
    }

    @Test
    @DisplayName("TC-RT-10 refreshing the verification page does not disclose an issued code again")
    void otpShowsOnce() throws Exception {
        var session = session(mvc.perform(login("rahul@example.com", "Demo@1234")).andReturn());
        var first = mvc.perform(get("/verify-mobile").with(requestedSession(session))).andExpect(status().isOk()).andReturn();
        var second = mvc.perform(get("/verify-mobile").with(requestedSession(session))).andExpect(status().isOk()).andReturn();
        assertThat(otp(first).demoCode()).matches("[0-9]{6}");
        assertThat(otp(second).demoCode()).isNull();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM mobile_otp").query(Integer.class).single()).isOne();
    }

    @Test
    @DisplayName("TC-UC-04b an incorrect OTP reports remaining attempts and a correctly spaced code still succeeds")
    void wrongThenCorrect() throws Exception {
        var session = session(mvc.perform(login("rahul@example.com", "Demo@1234")).andReturn());
        var result = mvc.perform(get("/verify-mobile").with(requestedSession(session))).andReturn();
        String code = otp(result).demoCode();
        String wrong = code.equals("000000") ? "000001" : "000000";
        for (String message : java.util.List.of("Incorrect OTP, 2 attempts left.", "Incorrect OTP, 1 attempt left.")) {
            mvc.perform(post("/verify-mobile").with(requestedSession(session)).with(csrf()).param("code", wrong))
                    .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "code"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(message)));
        }
        mvc.perform(post("/verify-mobile").with(requestedSession(session)).with(csrf())
                        .param("code", code.substring(0, 3) + " " + code.substring(3)))
                .andExpect(redirectedUrl("/citizen/complaints"));
    }

    @Test
    @DisplayName("TC-SEC-48 registration refuses personal passwords and overlong UTF-8 passwords")
    void rejectsPersonalPassword() throws Exception {
        mvc.perform(register("priya@example.com", "9820000009", "priya@12345", "priya@12345"))
                .andExpect(model().attributeHasFieldErrorCode("form", "password", "validation.password.personal"));
        mvc.perform(register("priya@example.com", "9820000009", "अ".repeat(30) + "2", "अ".repeat(30) + "2"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Password is too long")));
    }

    @Test
    @DisplayName("TC-BR-31 deactivation ends both HTML and notification sessions without touching complaints")
    void deactivationEndsEverySession() throws Exception {
        var first = session(mvc.perform(login("jayesh@example.com", "Demo@1234")).andReturn());
        var second = session(mvc.perform(login("jayesh@example.com", "Demo@1234")).andReturn());
        var complaints = jdbc.sql("SELECT COUNT(*) FROM complaint").query(Integer.class).single();
        jdbc.sql("UPDATE app_user SET active = FALSE, version = version + 1 WHERE id = :id")
                .param("id", user(first).id()).update();
        mvc.perform(get("/citizen/complaints").with(requestedSession(first))).andExpect(redirectedUrl("/login?inactive"));
        mvc.perform(get("/notifications/unread-count").with(requestedSession(second)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("unauthenticated"));
        assertThat(first.isInvalid()).isTrue();
        assertThat(second.isInvalid()).isTrue();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM complaint").query(Integer.class).single()).isEqualTo(complaints);
    }

    @ParameterizedTest
    @CsvSource({"5800000001", "98000000"})
    @DisplayName("TC-BR-32 invalid mobile numbers are refused and formatted Indian mobiles are stored as digits")
    void mobileRules(String invalid) throws Exception {
        mvc.perform(register("priya@example.com", invalid, "Strong@123", "Strong@123"))
                .andExpect(model().attributeHasFieldErrors("form", "mobile"));
        var result = mvc.perform(register("priya@example.com", "+91 98765-43210", "Strong@123", "Strong@123"))
                .andExpect(redirectedUrl("/verify-mobile")).andReturn();
        assertThat(accounts.find(user(session(result)).id()).orElseThrow().mobile()).isEqualTo("9876543210");
    }

    @Test
    @DisplayName("TC-SEC-03 an unauthorized count refresh never replaces the saved login target")
    void countNeverSaved() throws Exception {
        var guest = session(mvc.perform(get("/login")).andReturn());
        mvc.perform(get("/notifications/unread-count").with(requestedSession(guest)))
                .andExpect(status().isUnauthorized());
        mvc.perform(login("anita.deshmukh@example.com", "Demo@1234").with(requestedSession(guest)))
                .andExpect(redirectedUrl("/officer/queue"));
    }

    @Test
    @DisplayName("TC-SEC-11 a rejected request redirects differently for expired and absent sessions")
    void csrfWithoutAuthenticatedSession() throws Exception {
        mvc.perform(post("/verify-mobile").with(invalidSession()).with(csrf().useInvalidToken()))
                .andExpect(redirectedUrl("/login?expired"));
        mvc.perform(post("/verify-mobile")).andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("TC-SEC-13 automatic login rotates both the session identifier and its CSRF token")
    void registrationRotatesCsrf() throws Exception {
        var screen = mvc.perform(get("/register")).andExpect(status().isOk()).andReturn();
        var guest = session(screen);
        String beforeId = guest.getId();
        var oldToken = csrfToken(screen);
        mvc.perform(post("/register").with(ip()).with(requestedSession(guest))
                        .param("_csrf", oldToken).param("fullName", "Priya Nair")
                        .param("email", "priya@example.com").param("mobile", "9820000009")
                        .param("password", "Strong@123").param("confirmPassword", "Strong@123"))
                .andExpect(redirectedUrl("/verify-mobile"));
        assertThat(guest.getId()).isNotEqualTo(beforeId);
        mvc.perform(post("/verify-mobile/resend").with(requestedSession(guest)).param("_csrf", oldToken))
                .andExpect(status().isForbidden());
        var verify = mvc.perform(get("/verify-mobile").with(requestedSession(guest)))
                .andExpect(status().isOk()).andReturn();
        mvc.perform(post("/verify-mobile/resend").with(requestedSession(guest)).param("_csrf", csrfToken(verify)))
                .andExpect(redirectedUrl("/verify-mobile")).andExpect(flash().attributeExists("flashError"));
    }

    @Test
    @DisplayName("TC-SEC-28 an external verification change refreshes the principal without a login")
    void verificationRefresh() throws Exception {
        var session = session(mvc.perform(login("rahul@example.com", "Demo@1234")).andReturn());
        var id = session.getId();
        assertThat(user(session).mobileVerified()).isFalse();
        jdbc.sql("UPDATE app_user SET mobile_verified = TRUE, version = version + 1 WHERE id = :id")
                .param("id", user(session).id()).update();
        mvc.perform(get("/citizen/complaints").with(requestedSession(session))).andExpect(status().isOk());
        assertThat(session.getId()).isEqualTo(id);
        assertThat(user(session).mobileVerified()).isTrue();
    }

    @Test
    @DisplayName("TC-SEC-52 stored authentication retains neither the credential nor the BCrypt hash")
    void storedPrincipalHasNoCredentials() throws Exception {
        var session = session(mvc.perform(login("mahesh@example.com", "Demo@1234")).andReturn());
        var context = (SecurityContext) session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context.getAuthentication().getCredentials()).isNull();
        assertThat(user(session).getPassword()).isNull();
        assertThat(user(session).toString()).doesNotContain("Demo@1234", "mahesh@example.com", "$2a$");
    }

    private static String csrfToken(MvcResult response) throws java.io.UnsupportedEncodingException {
        return Jsoup.parse(response.getResponse().getContentAsString()).selectFirst("input[name=_csrf]").val();
    }

    private static com.smartgrievance.otp.OtpStatusView otp(MvcResult response) {
        return (com.smartgrievance.otp.OtpStatusView) response.getModelAndView().getModel().get("otp");
    }

    private MockHttpServletRequestBuilder register(
            String email, String mobile, String password, String confirmation) {
        return post("/register").with(csrf()).with(ip()).param("fullName", "Priya Nair").param("email", email)
                .param("mobile", mobile).param("password", password).param("confirmPassword", confirmation);
    }

    private MockHttpServletRequestBuilder login(String email, String password) {
        return post("/login").with(csrf()).with(ip()).param("email", email).param("password", password);
    }

    private RequestPostProcessor ip() {
        return request -> {
            request.setRemoteAddr(clientIp);
            return request;
        };
    }

    private static MockHttpSession session(MvcResult result) {
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static CurrentUser user(MockHttpSession session) {
        var context = (SecurityContext) session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        return (CurrentUser) context.getAuthentication().getPrincipal();
    }
}
