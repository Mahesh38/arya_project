package com.smartgrievance.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartgrievance.config.ProdSafetyCheck.Finding;
import com.smartgrievance.config.ProdSafetyCheck.Inputs;
import com.smartgrievance.config.ProdSafetyCheck.Level;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ProdSafetyCheckTest {
    private static final Inputs SAFE = new Inputs(false, false, "grievance.cell@pmc.example.in", "Grv#Cell!2027x",
            "9123456780", "db-secret", true, "sms", 12, "jdbc:mysql://localhost:3306/grievance");

    @Test
    @DisplayName("TC-SEC-53 a safe production configuration passes without findings")
    void safeConfigurationPasses() {
        assertThat(ProdSafetyCheck.evaluate(SAFE)).isEmpty();
        assertThatCode(() -> ProdSafetyCheck.enforce(List.of())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("TC-SEC-53 unsafe production start-ups are refused (P-1, P-2, P-3, P-7)")
    void refusals() {
        assertThat(checks(with(true, false, "Grv#Cell!2027x", "db-secret", 12))).containsExactly("P-1");
        for (String weak : new String[] {null, "", "Admin@1234", "password1", "short1"}) {
            assertThat(checks(with(false, false, weak, "db-secret", 12))).as(String.valueOf(weak)).containsExactly("P-2");
        }
        assertThat(checks(with(false, true, "Admin@1234", "db-secret", 12)))
                .as("an existing admin makes the bootstrap password irrelevant").isEmpty();
        assertThat(checks(with(false, false, "Grv#Cell!2027x", "", 12))).containsExactly("P-3");
        assertThat(checks(with(false, false, "Grv#Cell!2027x", null, 12))).containsExactly("P-3");
        assertThat(checks(with(false, false, "Grv#Cell!2027x", "db-secret", 9))).containsExactly("P-7");
        assertThatThrownBy(() -> ProdSafetyCheck.enforce(ProdSafetyCheck.evaluate(with(false, false, "Admin@1234", "", 4))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("P-2").hasMessageContaining("P-3").hasMessageContaining("P-7")
                .hasMessageNotContaining("Admin@1234").hasMessageNotContaining("db-secret");
    }

    @Test
    @DisplayName("TC-SEC-53 the embedded H2 database of the local profile is refused in production (P-8)")
    void h2IsRefusedInProduction() {
        var inputs = new Inputs(false, true, SAFE.adminEmail(), null, null, "db-secret", true, "sms", 12,
                "jdbc:h2:file:./local-data/db/smart-grievance;MODE=MySQL");
        assertThat(checks(inputs)).containsExactly("P-8");
        assertThatThrownBy(() -> ProdSafetyCheck.enforce(ProdSafetyCheck.evaluate(inputs))).hasMessageContaining("P-8");
    }

    @Test
    @DisplayName("TC-SEC-53 insecure but tolerable settings only warn (P-4, P-5, P-6)")
    void warnings() {
        var inputs = new Inputs(false, true, "admin@example.com", null, null, "db-secret", false, "demo", 12, SAFE.datasourceUrl());
        var findings = ProdSafetyCheck.evaluate(inputs);
        assertThat(findings).extracting(Finding::check).containsExactly("P-4", "P-5", "P-6");
        assertThat(findings).extracting(Finding::level).containsOnly(Level.WARN);
        assertThatCode(() -> ProdSafetyCheck.enforce(findings)).doesNotThrowAnyException();
    }

    private static Inputs with(boolean dev, boolean adminExists, String password, String dbPassword, int strength) {
        return new Inputs(dev, adminExists, SAFE.adminEmail(), password, SAFE.adminMobile(), dbPassword, true, "sms", strength,
                SAFE.datasourceUrl());
    }

    private static List<String> checks(Inputs inputs) {
        return ProdSafetyCheck.evaluate(inputs).stream().map(Finding::check).toList();
    }
}
