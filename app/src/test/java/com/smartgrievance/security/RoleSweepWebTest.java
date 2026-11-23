package com.smartgrievance.security;

import static com.smartgrievance.testsupport.Logins.appUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.smartgrievance.seed.DemoData;
import com.smartgrievance.testsupport.WebTestBase;
import com.smartgrievance.testsupport.WithAppUser;
import com.smartgrievance.user.AccountBootstrap;
import com.smartgrievance.user.AppUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class RoleSweepWebTest extends WebTestBase {
    @Autowired private AccountBootstrap admin;
    @Autowired private DemoData demo;
    @Autowired private AppUserDetailsService users;
    @Autowired private JdbcClient jdbc;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping mappings;

    @BeforeEach
    void seedUsers() {
        admin.ensureAdmin(properties.admin(), false);
        demo.seedUsers();
    }

    @Test
    @DisplayName("TC-RT-G1 every M1 citizen-only route rejects both staff roles without changing state")
    void wrongRole() throws Exception {
        var before = entries();
        for (Route route : routes()) {
            if (!route.role().equals("CITIZEN")) continue;
            for (String email : List.of("admin@example.com", "anita.deshmukh@example.com")) {
                mvc.perform(request(HttpMethod.valueOf(route.method()), route.path())
                                .with(appUser(users, email)).with(csrf()).param("code", "123456"))
                        .andExpect(status().isForbidden());
            }
        }
        assertThat(entries()).isEqualTo(before);
    }

    @Test
    @WithAppUser("mahesh@example.com")
    @DisplayName("TC-RT-G2 a signed-in request for an unknown page returns 404")
    void unknownPage() throws Exception {
        var before = entries();
        mvc.perform(get("/account/unknown-page")).andExpect(status().isNotFound()).andExpect(view().name("error/404"));
        assertThat(entries()).isEqualTo(before);
    }

    @Test
    @DisplayName("TC-RT-G3 the anonymous sweep visits every protected M1 endpoint")
    void anonymous() throws Exception {
        for (Route route : routes()) {
            if (route.role().equals("PUBLIC")) continue;
            mvc.perform(request(HttpMethod.valueOf(route.method()), route.path()).with(csrf()))
                    .andExpect(redirectedUrl("/login"));
        }
        var count = mvc.perform(get("/notifications/unread-count")).andExpect(status().isUnauthorized()).andReturn();
        assertThat(count.getRequest().getSession(false)).isNull();
    }

    @Test
    @DisplayName("TC-RT-G4 all M1 routes agree with the documented method and path inventory")
    void routeInventory() throws IOException {
        var documented = routes().stream().map(Route::key).collect(Collectors.toSet());
        var interimRoutes = Set.of("GET /citizen/complaints", "GET /citizen/complaints/new", "GET /officer/queue",
                "GET /admin/dashboard", "GET /notifications/unread-count");
        var actual = mappings.getHandlerMethods().entrySet().stream()
                .filter(mapping -> mapping.getValue().getBeanType().getPackageName().startsWith("com.smartgrievance"))
                .flatMap(mapping -> mapping.getKey().getPatternValues().stream()
                        .flatMap(path -> mapping.getKey().getMethodsCondition().getMethods().stream()
                                .map(method -> method.name() + " " + path)))
                .filter(endpoint -> !interimRoutes.contains(endpoint))
                .collect(Collectors.toSet());
        actual.add("POST /login");
        actual.add("POST /logout");
        assertThat(actual).isEqualTo(documented);
    }

    @Test
    @DisplayName("TC-SEC-16 role authorization rules cover all role landing paths")
    void roleLandingGuards() throws Exception {
        var roles = java.util.Map.of("mahesh@example.com", "/citizen/complaints",
                "anita.deshmukh@example.com", "/officer/queue", "admin@example.com", "/admin/dashboard");
        for (var origin : roles.entrySet()) {
            for (String target : roles.values()) {
                if (target.equals(origin.getValue())) continue;
                mvc.perform(get(target).with(appUser(users, origin.getKey()))).andExpect(status().isForbidden());
            }
        }
    }

    @Test
    @DisplayName("TC-SEC-17 both staff roles are rejected by the mobile verification page")
    void staffDoNotVerify() throws Exception {
        for (String account : List.of("anita.deshmukh@example.com", "admin@example.com")) {
            mvc.perform(get("/verify-mobile").with(appUser(users, account))).andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("TC-SEC-19 only documented M1 role prefixes are exposed")
    void knownPrefixes() throws IOException {
        assertThat(routes().stream().filter(route -> !route.role().equals("PUBLIC")))
                .allSatisfy(route -> assertThat(route.path()).matches("/(logout|account/[^/]+|verify-mobile(?:/[^/]+)?)"));
    }

    private List<?> entries() {
        return jdbc.sql("""
                SELECT id, active, mobile_verified, version, department_id, password_hash
                FROM app_user ORDER BY id
                """).query().listOfRows();
    }

    private static List<Route> routes() throws IOException {
        String csv = new ClassPathResource("routes.csv").getContentAsString(StandardCharsets.UTF_8);
        return csv.lines().skip(1).map(line -> line.split(",", 6))
                .filter(row -> Integer.parseInt(row[0].substring(2)) <= 12)
                .map(row -> new Route(row[1], row[2], row[3]))
                .toList();
    }

    private record Route(String method, String path, String role) {
        String key() { return method + " " + path; }
    }
}
