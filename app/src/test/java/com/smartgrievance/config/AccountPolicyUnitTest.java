package com.smartgrievance.config;

import java.util.Properties;

import com.smartgrievance.common.validation.PasswordPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class AccountPolicyUnitTest {
    @ParameterizedTest
    @CsvSource({"application.properties,12", "application-test.properties,4"})
    @DisplayName("TC-SEC-56 the configured credential encoder uses BCrypt at the expected profile cost")
    void bcryptCost(String profileProperties, int cost) throws Exception {
        var properties = new Properties();
        try (var input = new ClassPathResource(profileProperties).getInputStream()) {
            properties.load(input);
        }
        int configured = Integer.parseInt(properties.getProperty("app.security.bcrypt-strength"));
        var security = new AppProperties.Security(configured, null, null);
        var encoder = new SecurityConfig().passwordEncoder(
                new AppProperties(null, null, null, null, null, null, security, null));

        String sample = "Nacre!Cascade77";
        String hash = encoder.encode(sample);
        assertThat(hash).matches("[$]2[aby][$]%02d[$].{53}".formatted(cost));
        assertThat(encoder.matches(sample, hash)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"mahesh1Planet,true", "MAHESH2Planet,true", "9800000001-Nacre,true", "Nacre!Cascade77,false"})
    @DisplayName("TC-UT-08 the password policy rejects account information case-insensitively")
    void personalData(String candidate, boolean containsPersonalData) {
        assertThat(PasswordPolicy.containsPersonalData(candidate, "mahesh@example.com", "9800000001"))
                .isEqualTo(containsPersonalData);
    }
}
