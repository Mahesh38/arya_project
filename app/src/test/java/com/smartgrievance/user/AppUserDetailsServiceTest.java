package com.smartgrievance.user;

import com.smartgrievance.seed.DemoData;
import com.smartgrievance.testsupport.FlowTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class AppUserDetailsServiceTest extends FlowTestBase {
    @Autowired AppUserDetailsService identities;
    @Autowired DemoData demo;
    @Autowired PasswordEncoder encoder;

    @Test
    void credentialUpgradeReloadsTheAccountAndRefusesAStalePrincipal() {
        demo.seedUsers();
        var current = identities.loadUserByUsername(" MAHESH@EXAMPLE.COM ");
        String newHash = encoder.encode("New!FixturePassword92");
        var updated = (CurrentUser) identities.updatePassword(current, newHash);
        assertThat(updated.version()).isEqualTo(current.version() + 1);
        assertThat(updated.getPassword()).isEqualTo(newHash);
        assertThat(identities.updatePassword(current, encoder.encode("OldVersion!FixturePassword92"))).isSameAs(current);
        assertThat(identities.loadUserByUsername(current.getUsername()).getPassword()).isEqualTo(newHash);
    }

    @Test
    void missingAccountAndBlankIdentityAreRejected() {
        assertThat(AppUserDetailsService.normalizeEmail(null)).isEmpty();
        assertThatExceptionOfType(UsernameNotFoundException.class)
                .isThrownBy(() -> identities.loadUserByUsername("absent@example.test"));
    }
}
