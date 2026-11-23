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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private static final Pattern ROUTE_ALTERNATIVES = Pattern.compile("\\{[A-Za-z]+:([a-z-]+(?:\\|[a-z-]+)+)}");
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
    @DisplayName("TC-RT-G1 citizen and admin routes reject the other roles without changing state")
    void wrongRole() throws Exception {
        var before = entries();
        for (Route route : routes()) {
            var wrongAccounts = switch (route.role()) {
                case "CITIZEN" -> List.of("admin@example.com", "anita.deshmukh@example.com");
                case "OFFICER" -> List.of("mahesh@example.com", "admin@example.com");
                case "ADMIN" -> List.of("mahesh@example.com", "anita.deshmukh@example.com");
                default -> List.<String>of();
            };
            for (String email : wrongAccounts) {
                mvc.perform(request(HttpMethod.valueOf(route.method()), route.samplePath())
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
    @DisplayName("TC-RT-G3 the anonymous sweep visits every implemented protected endpoint")
    void anonymous() throws Exception {
        for (Route route : routes()) {
            if (route.role().equals("PUBLIC")) continue;
            mvc.perform(request(HttpMethod.valueOf(route.method()), route.samplePath()).with(csrf()))
                    .andExpect(redirectedUrl("/login"));
        }
        var count = mvc.perform(get("/notifications/unread-count")).andExpect(status().isUnauthorized()).andReturn();
        assertThat(count.getRequest().getSession(false)).isNull();
    }

    @Test
    @DisplayName("TC-RT-G4 the implemented routes match the documented method and path inventory")
    void routeInventory() throws IOException {
        var documented = routes().stream().map(Route::key).collect(Collectors.toSet());
        var interimRoutes = Set.of("GET /admin/dashboard", "GET /notifications/unread-count");
        var actual = mappings.getHandlerMethods().entrySet().stream()
                .filter(mapping -> mapping.getValue().getBeanType().getPackageName().startsWith("com.smartgrievance"))
                .flatMap(mapping -> mapping.getKey().getPatternValues().stream()
                        .flatMap(RoleSweepWebTest::expandAlternatives)
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
    @DisplayName("TC-SEC-19 only documented account and admin role prefixes are exposed")
    void knownPrefixes() throws IOException {
        assertThat(routes().stream().filter(route -> !route.role().equals("PUBLIC")))
                .allSatisfy(route -> assertThat(route.path()).matches(
                        "/(logout|account/[^/]+|verify-mobile(?:/[^/]+)?|citizen/(?:complaints(?:/.*)?|watching)|complaints/[^/]+|attachments/[^/]+|officer/(queue|complaints)(?:/.*)?|admin/(complaints|departments|categories|areas|officers|citizens)(?:/.*)?)"));
    }

    private List<?> entries() {
        return Stream.of("app_user", "mobile_otp", "department", "category", "area", "complaint", "complaint_event")
                .map(table -> jdbc.sql("SELECT * FROM " + table + " ORDER BY id").query().listOfRows())
                .toList();
    }

    private static List<Route> routes() throws IOException {
        String csv = new ClassPathResource("routes.csv").getContentAsString(StandardCharsets.UTF_8);
        return csv.lines().skip(1).map(line -> line.split(",", 6))
                .filter(row -> {
                    int id = Integer.parseInt(row[0].substring(2));
                    return id <= 12 || (id >= 20 && id <= 31) || (id >= 40 && id <= 53)
                            || (id >= 56 && id <= 62) || (id >= 65 && id <= 70) || id == 73 || (id >= 80 && id <= 115);
                })
                .map(row -> new Route(row[1], row[2], row[3], row[4]))
                .toList();
    }

    private static Stream<String> expandAlternatives(String path) {
        var match = ROUTE_ALTERNATIVES.matcher(path);
        if (!match.find()) return Stream.of(path);
        String prefix = path.substring(0, match.start());
        String suffix = path.substring(match.end());
        return Stream.of(match.group(1).split("\\|"))
                .flatMap(option -> expandAlternatives(prefix + option + suffix));
    }

    private record Route(String method, String path, String role, String variables) {
        String key() { return method + " " + path; }

        String samplePath() {
            String result = path;
            for (String entry : variables.split(";")) {
                if (entry.isEmpty()) continue;
                String[] parts = entry.split("=", 2);
                result = result.replace("{" + parts[0] + "}", parts[1]);
            }
            return result;
        }
    }
}
