package com.smartgrievance.config;

import com.smartgrievance.common.validation.PasswordPolicy;
import com.smartgrievance.security.SecurityEvents;
import com.smartgrievance.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Doc 11 §9.2 (D73): start-up checks for the prod profile. Refusals (P-1, P-2, P-3, P-7) stop the application with a
 * clear message; warnings (P-4, P-5, P-6) are logged as {@code STARTUP_CHECK}. Runs before {@code AdminBootstrap}
 * ({@code @Order(1)}). Missing DB variables are caught earlier by {@link ProdEnvironmentCheck}.
 */
@Component
@Profile("prod")
@Order(0)
public class ProdSafetyCheck implements ApplicationRunner {
    static final String DEFAULT_ADMIN_PASSWORD = "Admin@1234";
    static final int MIN_BCRYPT_STRENGTH = 10;

    public enum Level { REFUSE, WARN }
    public record Finding(String check, Level level, String message) {}
    public record Inputs(boolean devProfileActive, boolean adminExists, String adminEmail, String adminPassword,
                         String adminMobile, String datasourcePassword, boolean cookieSecure, String otpSender,
                         int bcryptStrength, String datasourceUrl, boolean h2ConsoleEnabled) {}

    private final AppProperties properties;
    private final Environment environment;
    private final UserRepository users;

    ProdSafetyCheck(AppProperties properties, Environment environment, UserRepository users) {
        this.properties = properties;
        this.environment = environment;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        var inputs = new Inputs(environment.matchesProfiles("dev") || environment.matchesProfiles("local"), users.adminExists(), properties.admin().email(),
                properties.admin().password(), properties.admin().mobile(),
                environment.getProperty("spring.datasource.password"),
                environment.getProperty("server.servlet.session.cookie.secure", Boolean.class, false),
                properties.otp().sender(), properties.security().bcryptStrength(),
                environment.getProperty("spring.datasource.url"),
                environment.getProperty("spring.h2.console.enabled", Boolean.class, false));
        enforce(evaluate(inputs));
    }

    /** Pure rule evaluation, unit-tested by TC-SEC-53. */
    public static List<Finding> evaluate(Inputs in) {
        var findings = new ArrayList<Finding>();
        if (in.devProfileActive()) {
            findings.add(new Finding("P-1", Level.REFUSE, "the dev or local profile must not be combined with prod"));
        }
        String password = in.adminPassword();
        if (!in.adminExists() && (password == null || password.isBlank() || DEFAULT_ADMIN_PASSWORD.equals(password)
                || !PasswordPolicy.isStrong(password)
                || PasswordPolicy.containsPersonalData(password, in.adminEmail(), in.adminMobile()))) {
            findings.add(new Finding("P-2", Level.REFUSE,
                    "no administrator exists yet; set a strong ADMIN_PASSWORD (not the default) before the first start"));
        }
        if (in.datasourcePassword() == null || in.datasourcePassword().isBlank()) {
            findings.add(new Finding("P-3", Level.REFUSE, "the database password (DB_PASSWORD) is empty"));
        }
        if (!in.cookieSecure()) {
            findings.add(new Finding("P-4", Level.WARN, "Session cookie is not marked Secure; use HTTPS in production"));
        }
        if ("demo".equals(in.otpSender())) {
            findings.add(new Finding("P-5", Level.WARN,
                    "OTP sender is 'demo': codes are shown on screen and mobile numbers are not really verified"));
        }
        if (in.adminEmail() != null && in.adminEmail().toLowerCase(java.util.Locale.ROOT).endsWith("@example.com")) {
            findings.add(new Finding("P-6", Level.WARN, "ADMIN_EMAIL is an example.com address"));
        }
        if (in.bcryptStrength() < MIN_BCRYPT_STRENGTH) {
            findings.add(new Finding("P-7", Level.REFUSE,
                    "app.security.bcrypt-strength must be at least " + MIN_BCRYPT_STRENGTH));
        }
        if (in.h2ConsoleEnabled()) {
            findings.add(new Finding("P-8", Level.REFUSE, "the H2 console is for the local profile only (D131)"));
        } else if (in.datasourceUrl() != null && in.datasourceUrl().startsWith("jdbc:h2:")) {
            findings.add(new Finding("P-8", Level.REFUSE,
                    "the embedded H2 database is for the local profile only; use MySQL in production (D130)"));
        }
        return List.copyOf(findings);
    }

    static void enforce(List<Finding> findings) {
        var refusals = new ArrayList<String>();
        for (var finding : findings) {
            if (finding.level() == Level.REFUSE) {
                SecurityEvents.LOG.error("STARTUP_CHECK check={} result=refused", finding.check());
                refusals.add(finding.check() + ": " + finding.message());
            } else {
                SecurityEvents.LOG.warn("STARTUP_CHECK check={} result=warning message=\"{}\"", finding.check(), finding.message());
            }
        }
        if (!refusals.isEmpty()) {
            throw new IllegalStateException("prod start-up refused: " + String.join("; ", refusals));
        }
    }
}
