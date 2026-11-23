package com.smartgrievance.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * doc 14 §5.7 (D112): runs before any bean is created. In the prod profile it stops the start-up
 * with a plain message when a required environment variable is missing, instead of a deep
 * "'url' must start with jdbc" stack trace. ProdSafetyCheck (doc 11 §9.2) does the remaining checks later.
 */
public class ProdEnvironmentCheck implements EnvironmentPostProcessor, Ordered {

    private static final Map<String, String> REQUIRED = new LinkedHashMap<>();
    static {
        REQUIRED.put("spring.datasource.url", "DB_URL");
        REQUIRED.put("spring.datasource.username", "DB_USERNAME");
        REQUIRED.put("spring.datasource.password", "DB_PASSWORD");
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        if (!env.matchesProfiles("prod")) {
            return;
        }
        if (env.matchesProfiles("dev")) {
            throw new IllegalStateException("P-1: the dev and prod profiles must not be combined");
        }
        List<String> missing = new ArrayList<>();
        REQUIRED.forEach((property, variable) -> {
            String value;
            try {
                value = env.getProperty(property);
            } catch (IllegalArgumentException unresolvedPlaceholder) {
                value = null;
            }
            if (value == null || value.isBlank()) {
                missing.add(variable);
            }
        });
        if (!missing.isEmpty()) {
            throw new IllegalStateException("prod profile: required environment variable(s) not set: "
                    + String.join(", ", missing) + " (see docs/specs/14-build-and-deployment.md §5.6)");
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
