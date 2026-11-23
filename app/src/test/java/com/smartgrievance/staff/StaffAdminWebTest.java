package com.smartgrievance.staff;

import com.smartgrievance.common.PageResult;
import com.smartgrievance.common.exception.BusinessRuleException;
import com.smartgrievance.common.web.AdminPageLinks;
import com.smartgrievance.masterdata.LookupOption;
import com.smartgrievance.security.LoginAttemptService;
import com.smartgrievance.seed.DemoData;
import com.smartgrievance.testsupport.Logins;
import com.smartgrievance.testsupport.Sessions;
import com.smartgrievance.testsupport.TestDatabase;
import com.smartgrievance.testsupport.WebTestBase;
import com.smartgrievance.user.AccountBootstrap;
import com.smartgrievance.user.AppUserDetailsService;
import com.smartgrievance.user.Role;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StaffAdminWebTest extends WebTestBase {
    private static final String OFFICERS = "/admin/officers";
    private static final String CITIZENS = "/admin/citizens";
    private static final String ROHAN = "rohan.kulkarni@example.com";
    private static final String ANITA = "anita.deshmukh@example.com";
    private static final String VIKRAM = "vikram.jadhav@example.com";
    private static final String KIRAN = "kiran.more@example.com";
    private static final String RAHUL = "rahul@example.com";
    private static final String JAYESH = "jayesh@example.com";
    private static final String SNEHA = "sneha@example.com";
    private static final String DEMO = "Demo@1234";
    private static final String REPLACEMENT = "Nacre!Cascade77";
    private static final String OFFICER_EMAIL = "neha.new@example.test";
    private static final AtomicInteger NEXT_ADDRESS = new AtomicInteger();

    @Autowired AccountBootstrap bootstrap;
    @Autowired AppUserDetailsService details;
    @Autowired DemoData demoData;
    @Autowired JdbcClient jdbc;
    @Autowired UserAdminService staff;
    @Autowired PasswordEncoder passwords;
    @Autowired LoginAttemptService attempts;
    private String address;

    @BeforeEach
    void users() {
        bootstrap.ensureAdmin(properties.admin(), false);
        demoData.seedUsers();
        address = "198.18." + NEXT_ADDRESS.incrementAndGet() / 250 + "." + NEXT_ADDRESS.get() % 250;
        for (String email : List.of(ROHAN, VIKRAM, ANITA, KIRAN, RAHUL, JAYESH, SNEHA, OFFICER_EMAIL)) {
            attempts.clearAccount(email);
        }
    }

    static IntStream routes() { return IntStream.rangeClosed(101, 115); }

    @ParameterizedTest(name = "TC-RT-{0} staff route")
    @MethodSource("routes")
    void contracts(int route) throws Exception {
        switch (route) {
            case 101 -> {
                assertThat(page(read(OFFICERS).andExpect(view().name("admin/officers")).andReturn()).totalItems()).isEqualTo(7);
                var result = mvc.perform(get(OFFICERS).with(admin()).queryParam("q", "Anita").queryParam("departmentId",
                                Long.toString(departmentId("Water Supply"))).queryParam("active", "true"))
                        .andExpect(status().isOk()).andReturn();
                assertThat(page(result).items()).extracting(row -> ((UserRow) row).email()).containsExactly(ANITA);
            }
            case 102 -> {
                var page = read(OFFICERS + "/new").andExpect(view().name("admin/officer-form"))
                        .andExpect(model().attribute("mode", "CREATE")).andReturn();
                var options = (List<?>) page.getModelAndView().getModel().get("departments");
                assertThat(options).allSatisfy(option -> assertThat(((LookupOption) option).active()).isTrue());
            }
            case 103 -> {
                mvc.perform(officerForm(OFFICER_EMAIL, "7800112233").param("role", "ADMIN"))
                        .andExpect(redirectedUrl(OFFICERS)).andExpect(flash().attribute("flashSuccess", "Officer account created."));
                var user = account(OFFICER_EMAIL);
                assertThat(user.role()).isEqualTo(Role.OFFICER);
                assertThat(user.active()).isTrue();
                assertThat(user.verified()).isTrue();
            }
            case 104 -> {
                var officer = account(ANITA);
                var page = read(OFFICERS + "/" + officer.id() + "/edit")
                        .andExpect(model().attribute("mode", "EDIT")).andReturn();
                assertThat(((OfficerEditForm) page.getModelAndView().getModel().get("form")).getVersion()).isEqualTo(officer.version());
                gone(OFFICERS + "/" + account(JAYESH).id() + "/edit");
            }
            case 105 -> {
                var officer = account(ANITA);
                mvc.perform(editOfficer(officer, "Anita Deshmukh", "9000000201", "Drainage")
                                .param("role", "ADMIN").param("email", "other@example.test"))
                        .andExpect(redirectedUrl(OFFICERS)).andExpect(flash().attribute("flashSuccess", "Officer updated."));
                var changed = account(ANITA);
                assertThat(changed.version()).isEqualTo(officer.version() + 1);
                assertThat(changed.role()).isEqualTo(Role.OFFICER);
                mvc.perform(editOfficer(officer, "Anita Deshmukh", "9000000201", "Drainage"))
                        .andExpect(flash().attribute("flashError", "This account was changed by someone else, please refresh."));
            }
            case 106 -> activation(OFFICERS, KIRAN, true);
            case 107 -> {
                var session = session(ROHAN);
                activation(OFFICERS, ROHAN, false);
                mvc.perform(get("/officer/queue").with(Sessions.requestedSession(session)))
                        .andExpect(redirectedUrl("/login?inactive"));
            }
            case 108 -> {
                read(OFFICERS + "/" + account(ROHAN).id() + "/password").andExpect(view().name("admin/reset-password"))
                        .andExpect(model().attribute("backUrl", OFFICERS));
                gone(OFFICERS + "/" + account(SNEHA).id() + "/password");
            }
            case 109 -> {
                lock(ROHAN);
                reset(OFFICERS, ROHAN, REPLACEMENT).andExpect(redirectedUrl(OFFICERS));
                mvc.perform(login(ROHAN, REPLACEMENT)).andExpect(redirectedUrl("/officer/queue"));
            }
            case 110 -> {
                assertThat(page(read(CITIZENS).andExpect(view().name("admin/citizens")).andReturn()).totalItems()).isEqualTo(4);
                var list = page(mvc.perform(get(CITIZENS).with(admin()).queryParam("q", "9800000004")
                        .queryParam("verified", "false").queryParam("active", "true")).andExpect(status().isOk()).andReturn());
                assertThat(list.items()).extracting(row -> ((UserRow) row).email()).containsExactly(RAHUL);
            }
            case 111 -> {
                activation(CITIZENS, JAYESH, false);
                activation(CITIZENS, JAYESH, true);
            }
            case 112 -> {
                var session = session(JAYESH);
                activation(CITIZENS, JAYESH, false);
                mvc.perform(get("/citizen/complaints").with(Sessions.requestedSession(session)))
                        .andExpect(redirectedUrl("/login?inactive"));
            }
            case 113 -> {
                markVerified(account(RAHUL)).andExpect(redirectedUrl(CITIZENS))
                        .andExpect(flash().attribute("flashSuccess", "Mobile marked as verified."));
                markVerified(account(RAHUL)).andExpect(flash().attribute("flashError", "This mobile number is already verified."));
            }
            case 114 -> {
                read(CITIZENS + "/" + account(SNEHA).id() + "/password").andExpect(view().name("admin/reset-password"))
                        .andExpect(model().attribute("backUrl", CITIZENS));
                gone(CITIZENS + "/" + account(ROHAN).id() + "/password");
            }
            case 115 -> {
                reset(CITIZENS, SNEHA, REPLACEMENT).andExpect(redirectedUrl(CITIZENS))
                        .andExpect(flash().attributeExists("flashSuccess"));
                assertThat(passwords.matches(REPLACEMENT, account(SNEHA).hash())).isTrue();
            }
            default -> throw new AssertionError(route);
        }
    }

    @Test
    @DisplayName("TC-UC-43a creating an officer enables verified login with the assigned department")
    void createOfficer() throws Exception {
        mvc.perform(officerForm(OFFICER_EMAIL, "7800112233")).andExpect(redirectedUrl(OFFICERS));
        var created = account(OFFICER_EMAIL);
        assertThat(created.verified()).isTrue();
        assertThat(created.departmentId()).isEqualTo(departmentId("Drainage"));
        read(OFFICERS).andExpect(content().string(containsString(OFFICER_EMAIL)));
        var session = (MockHttpSession) mvc.perform(login(OFFICER_EMAIL, REPLACEMENT))
                .andExpect(redirectedUrl("/officer/queue")).andReturn().getRequest().getSession(false);
        mvc.perform(get("/officer/queue").with(Sessions.requestedSession(session)))
                .andExpect(content().string(containsString("Drainage")));
    }

    @Test
    @DisplayName("TC-UC-43b registration and staff creation share global email and mobile uniqueness")
    void duplicateOfficer() throws Exception {
        mvc.perform(officerForm(" MAHESH@EXAMPLE.COM ", "7800112233"))
                .andExpect(model().attributeHasFieldErrorCode("form", "email", "validation.email.taken"));
        mvc.perform(officerForm(OFFICER_EMAIL, "9800000002"))
                .andExpect(model().attributeHasFieldErrorCode("form", "mobile", "validation.mobile.taken"));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM app_user WHERE role = 'OFFICER'").query(Long.class).single()).isEqualTo(7);
    }

    @Test
    @DisplayName("TC-UC-43c a logged-in officer's next request reflects their changed department")
    void moveDepartment() throws Exception {
        var officer = account(VIKRAM);
        var session = session(VIKRAM);
        mvc.perform(editOfficer(officer, "Vikram Jadhav", "9000000301", "Drainage")).andExpect(redirectedUrl(OFFICERS));
        mvc.perform(get("/officer/queue").with(Sessions.requestedSession(session)))
                .andExpect(content().string(containsString("Drainage")));
        assertThat(account(VIKRAM).departmentId()).isEqualTo(departmentId("Drainage"));
    }

    @Test
    @DisplayName("TC-UC-43d deactivating one officer preserves complaints and other officers' access")
    void deactivateOfficer() throws Exception {
        complaintData();
        var before = departmentComplaints("Water Supply");
        activation(OFFICERS, ROHAN, false);
        mvc.perform(login(ROHAN, DEMO)).andExpect(redirectedUrl("/login?error"));
        var anita = session(ANITA);
        mvc.perform(get("/officer/queue").with(Sessions.requestedSession(anita))).andExpect(status().isOk());
        assertThat(departmentComplaints("Water Supply")).isNotEmpty().isEqualTo(before);
    }

    @Test
    @DisplayName("TC-UC-43e a staff password reset clears the login lock and invalidates the old password")
    void officerPasswordReset() throws Exception {
        lock(ROHAN);
        reset(OFFICERS, ROHAN, REPLACEMENT).andExpect(flash().attribute("flashSuccess",
                "Password reset. Give the new password to the user."));
        mvc.perform(login(ROHAN, DEMO)).andExpect(redirectedUrl("/login?error"));
        mvc.perform(login(ROHAN, REPLACEMENT)).andExpect(redirectedUrl("/officer/queue"));
    }

    @Test
    @DisplayName("TC-UC-44a an inactive citizen cannot log in and filed complaints remain stored")
    void deactivateCitizen() throws Exception {
        complaintData();
        long id = account(JAYESH).id();
        var before = ownedComplaints(id);
        activation(CITIZENS, JAYESH, false);
        mvc.perform(login(JAYESH, DEMO)).andExpect(redirectedUrl("/login?error"));
        assertThat(ownedComplaints(id)).isNotEmpty().isEqualTo(before);
    }

    @Test
    @DisplayName("TC-UC-44b a citizen can be manually verified and use a reset password")
    void citizenManagement() throws Exception {
        reset(CITIZENS, RAHUL, REPLACEMENT).andExpect(redirectedUrl(CITIZENS));
        var loggedIn = (MockHttpSession) mvc.perform(login(RAHUL, REPLACEMENT))
                .andExpect(redirectedUrl("/citizen/complaints")).andReturn().getRequest().getSession(false);
        markVerified(account(RAHUL)).andExpect(redirectedUrl(CITIZENS));
        assertThat(account(RAHUL).verified()).isTrue();
        mvc.perform(get("/verify-mobile").with(Sessions.requestedSession(loggedIn)))
                .andExpect(redirectedUrl("/citizen/complaints"));
        markVerified(account(RAHUL)).andExpect(flash().attribute("flashError", "This mobile number is already verified."));
    }

    @Test
    @DisplayName("TC-BR-29 an officer requires a department and the database bars a citizen department")
    void departmentRequired() throws Exception {
        mvc.perform(post(OFFICERS).with(admin()).with(csrf()).param("fullName", "Neha Testing")
                        .param("email", OFFICER_EMAIL).param("mobile", "7800112233").param("password", REPLACEMENT)
                        .param("confirmPassword", REPLACEMENT))
                .andExpect(model().attributeHasFieldErrors("form", "departmentId"));
        assertThatThrownBy(() -> jdbc.sql("UPDATE app_user SET department_id = :department WHERE id = :id")
                .param("department", departmentId("Drainage")).param("id", account(RAHUL).id()).update())
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_app_user_officer_dept");
    }

    @Test
    @DisplayName("TC-BR-30 the last active administrator cannot be deactivated")
    void lastAdmin() {
        var adminAccount = account(properties.admin().email());
        assertThatThrownBy(() -> staff.setActive(Role.ADMIN, adminAccount.id(), adminAccount.version(), false, adminAccount.id()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("error.admin.last");
        jdbc.sql("""
                INSERT INTO app_user (full_name, email, mobile, password_hash, role, active,
                    mobile_verified, created_at, updated_at)
                VALUES ('Second Admin', 'admin-two@example.test', '9000000007', :hash, 'ADMIN', TRUE, TRUE, :now, :now)
                """).param("hash", passwords.encode(REPLACEMENT))
                .param("now", LocalDateTime.now(testClock)).update();
        staff.setActive(Role.ADMIN, adminAccount.id(), adminAccount.version(), false, account("admin-two@example.test").id());
        assertThat(account(properties.admin().email()).active()).isFalse();
        assertThat(account("admin-two@example.test").active()).isTrue();
    }

    @Test
    @DisplayName("TC-BR-37 edits, activation, resets and verification increment account versions")
    void versions() throws Exception {
        var before = account(ANITA);
        mvc.perform(editOfficer(before, "Anita Deshmukh", "9000000201", "Drainage")).andExpect(redirectedUrl(OFFICERS));
        assertThat(account(ANITA).version()).isEqualTo(before.version() + 1);
        mvc.perform(post(OFFICERS + "/{id}/deactivate", before.id()).with(admin()).with(csrf())
                .param("version", Integer.toString(before.version()))).andExpect(flash().attributeExists("flashError"));
        activation(OFFICERS, ANITA, false);
        assertThat(account(ANITA).version()).isEqualTo(before.version() + 2);
        reset(OFFICERS, ANITA, REPLACEMENT).andExpect(redirectedUrl(OFFICERS));
        assertThat(account(ANITA).version()).isEqualTo(before.version() + 3);
        var citizen = account(RAHUL);
        markVerified(citizen).andExpect(redirectedUrl(CITIZENS));
        assertThat(account(RAHUL).version()).isEqualTo(citizen.version() + 1);
    }

    @Test
    @DisplayName("TC-SEC-07 an administrator reset clears the account lock without clearing its IP window")
    void resetClearsLock() throws Exception {
        lock(ROHAN);
        reset(OFFICERS, ROHAN, REPLACEMENT).andExpect(redirectedUrl(OFFICERS));
        mvc.perform(login(ROHAN, REPLACEMENT)).andExpect(redirectedUrl("/officer/queue"));
        for (int failure = 0; failure < 15; failure++) {
            mvc.perform(login("recover-" + address + "-" + failure + "@example.test", DEMO))
                    .andExpect(redirectedUrl("/login?error"));
        }
        mvc.perform(login(ROHAN, REPLACEMENT)).andExpect(redirectedUrl("/login?locked"));
    }

    @Test
    @DisplayName("TC-SEC-29b account routes refuse IDs belonging to a different role")
    void wrongRoleIsHidden() throws Exception {
        var citizen = account(SNEHA);
        var officer = account(ROHAN);
        gone(OFFICERS + "/" + citizen.id() + "/edit");
        gone(OFFICERS + "/" + citizen.id() + "/password");
        gone(CITIZENS + "/" + officer.id() + "/password");
        mvc.perform(editOfficer(citizen, "Sneha Kale", "9800000003", "Drainage")).andExpect(status().isNotFound());
        mvc.perform(post(OFFICERS + "/{id}/deactivate", citizen.id()).with(admin()).with(csrf())
                .param("version", Integer.toString(citizen.version()))).andExpect(status().isNotFound());
        mvc.perform(post(CITIZENS + "/{id}/activate", officer.id()).with(admin()).with(csrf())
                .param("version", Integer.toString(officer.version()))).andExpect(status().isNotFound());
        markVerified(officer).andExpect(status().isNotFound());
        reset(CITIZENS, ROHAN, REPLACEMENT).andExpect(status().isNotFound());
        assertThat(account(SNEHA)).isEqualTo(citizen);
        assertThat(account(ROHAN)).isEqualTo(officer);
    }

    @Test
    @DisplayName("TC-VE-02 officer edits keep the existing mobile and staff reset errors remain on the form")
    void staffValidation() throws Exception {
        var officer = account(ANITA);
        var missingVersion = post(OFFICERS + "/{id}", officer.id()).with(admin()).with(csrf())
                .param("fullName", "Anita Deshmukh").param("mobile", "9000000201")
                .param("departmentId", Long.toString(departmentId("Drainage")));
        mvc.perform(missingVersion).andExpect(model().attributeHasFieldErrors("form", "version"));
        mvc.perform(editOfficer(officer, "Anita Deshmukh", "9000000201", "Drainage")).andExpect(redirectedUrl(OFFICERS));
        reset(OFFICERS, ANITA, "weak1").andExpect(model().attributeHasFieldErrors("form", "newPassword"));
        reset(CITIZENS, RAHUL, "rahul!New71").andExpect(model().attributeHasFieldErrorCode("form", "newPassword",
                "validation.password.personal"));
        mvc.perform(post(OFFICERS + "/{id}/password", officer.id()).with(admin()).with(csrf())
                .param("newPassword", REPLACEMENT).param("confirmPassword", "Different!77"))
                .andExpect(model().attributeHasFieldErrors("form", "confirmPassword"));
        mvc.perform(post("/account/password").with(Logins.appUser(details, SNEHA)).with(csrf())
                .param("currentPassword", DEMO).param("newPassword", "weak1").param("confirmPassword", "weak1"))
                .andExpect(model().attributeHasFieldErrors("form", "newPassword"));
        mvc.perform(post("/verify-mobile").with(Logins.appUser(details, RAHUL)).with(csrf()).param("code", "11111"))
                .andExpect(model().attributeHasFieldErrors("form", "code"));
    }

    @Test
    void paginationRetainsLiteralPlusAndClampsPage() throws Exception {
        var result = mvc.perform(get(OFFICERS).with(admin()).queryParam("q", "Anita+Desk")
                        .queryParam("active", "true").queryParam("page", "-3").queryParam("size", "0"))
                .andExpect(status().isOk()).andReturn();
        assertThat(page(result).page()).isEqualTo(1);
        assertThat(page(result).size()).isEqualTo(1);
        var links = (AdminPageLinks) result.getModelAndView().getModel().get("pageLinks");
        assertThat(links.pages()).singleElement().satisfies(link -> {
            assertThat(link.url()).contains("q=Anita%2BDesk", "active=true", "page=1", "size=1");
            assertThat(link.current()).isTrue();
        });
    }

    private void complaintData() throws Exception {
        TestDatabase.reset(dataSource);
        TestDatabase.load(dataSource, "sql/q-fixture.sql");
        jdbc.sql("UPDATE app_user SET password_hash = :hash WHERE role IN ('OFFICER', 'CITIZEN')")
                .param("hash", passwords.encode(DEMO)).update();
        demoData.seedUsers();
    }

    private List<String> ownedComplaints(long id) {
        return jdbc.sql("SELECT complaint_no FROM complaint WHERE owner_id = :id ORDER BY complaint_no")
                .param("id", id).query(String.class).list();
    }

    private List<String> departmentComplaints(String department) {
        return jdbc.sql("SELECT complaint_no FROM complaint WHERE department_id = :id ORDER BY complaint_no")
                .param("id", departmentId(department)).query(String.class).list();
    }

    private MockHttpServletRequestBuilder officerForm(String email, String mobile) {
        return post(OFFICERS).with(admin()).with(csrf()).param("fullName", "Neha Testing").param("email", email)
                .param("mobile", mobile).param("departmentId", Long.toString(departmentId("Drainage")))
                .param("password", REPLACEMENT).param("confirmPassword", REPLACEMENT);
    }

    private MockHttpServletRequestBuilder editOfficer(Account user, String name, String mobile, String department) {
        return post(OFFICERS + "/{id}", user.id()).with(admin()).with(csrf())
                .param("fullName", name).param("mobile", mobile).param("version", Integer.toString(user.version()))
                .param("departmentId", Long.toString(departmentId(department)));
    }

    private ResultActions activation(String base, String email, boolean active) throws Exception {
        var user = account(email);
        var result = mvc.perform(post(base + "/{id}/" + (active ? "activate" : "deactivate"), user.id())
                        .with(admin()).with(csrf()).param("version", Integer.toString(user.version())))
                .andExpect(redirectedUrl(base)).andExpect(flash().attributeExists("flashSuccess"));
        assertThat(account(email).active()).isEqualTo(active);
        return result;
    }

    private ResultActions markVerified(Account user) throws Exception {
        return mvc.perform(post(CITIZENS + "/{id}/verify", user.id()).with(admin()).with(csrf())
                .param("version", Integer.toString(user.version())));
    }

    private ResultActions reset(String base, String email, String password) throws Exception {
        return mvc.perform(post(base + "/{id}/password", account(email).id()).with(admin()).with(csrf())
                .param("newPassword", password).param("confirmPassword", password));
    }

    private ResultActions read(String url) throws Exception { return mvc.perform(get(url).with(admin())).andExpect(status().isOk()); }
    private void gone(String url) throws Exception { mvc.perform(get(url).with(admin())).andExpect(status().isNotFound()); }

    private MockHttpServletRequestBuilder login(String email, String password) {
        return post("/login").with(csrf()).with(request -> {
            request.setRemoteAddr(address);
            return request;
        }).param("email", email).param("password", password);
    }

    private MockHttpSession session(String email) throws Exception {
        return (MockHttpSession) mvc.perform(login(email, DEMO)).andExpect(status().isFound())
                .andReturn().getRequest().getSession(false);
    }

    private void lock(String email) throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(login(email, "Wrong!7812")).andExpect(redirectedUrl("/login?error"));
        }
        mvc.perform(login(email, DEMO)).andExpect(redirectedUrl("/login?locked"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return Logins.appUser(details, properties.admin().email());
    }

    private long departmentId(String name) {
        return jdbc.sql("SELECT id FROM department WHERE name = :name").param("name", name).query(Long.class).single();
    }

    private Account account(String email) {
        return jdbc.sql("SELECT * FROM app_user WHERE email = :email").param("email", email).query((row, index) ->
                new Account(row.getLong("id"), Role.valueOf(row.getString("role")),
                        row.getObject("department_id", Long.class), row.getBoolean("active"),
                        row.getBoolean("mobile_verified"), row.getInt("version"), row.getString("password_hash"))).single();
    }

    private static PageResult<?> page(MvcResult result) {
        return (PageResult<?>) result.getModelAndView().getModel().get("page");
    }

    private record Account(long id, Role role, Long departmentId, boolean active, boolean verified, int version, String hash) {}
}
