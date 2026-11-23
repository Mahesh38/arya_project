package com.smartgrievance.auth;

import com.smartgrievance.common.exception.FormRejectedException;
import com.smartgrievance.otp.OtpService;
import com.smartgrievance.user.AccountService;
import com.smartgrievance.user.CurrentAccountService;
import com.smartgrievance.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("mysql")
@SpringBootTest
@ActiveProfiles({"test", "mysqltest"})
class AuthenticationMySqlIT {
    @Autowired AccountService accounts;
    @Autowired CurrentAccountService currentAccounts;
    @Autowired OtpService otp;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcClient jdbc;

    @Test
    @DisplayName("TC-NF-11 MySQL persists hashed credentials and committed OTP attempt state")
    void persistsAccountAndOtpState() {
        String run = Long.toString(System.currentTimeMillis());
        String originalPassword = "Nacre!Cascade77";
        String replacement = "Oleander!Moon92";
        var citizen = accounts.register("Priya Test", "mysql-auth-" + run + "@example.test",
                "79" + run.substring(run.length() - 8), originalPassword);
        long userId = citizen.id();
        try {
            assertThat(citizen.role()).isEqualTo(Role.CITIZEN);
            assertThat(citizen.mobileVerified()).isFalse();
            assertThat(citizen.passwordHash()).startsWith("$2");
            assertThat(passwords.matches(originalPassword, citizen.passwordHash())).isTrue();

            String demoCode = otp.status(userId, true).demoCode();
            assertThat(demoCode).matches("\\d{6}");
            String digest = jdbc.sql("SELECT otp_hash FROM mobile_otp WHERE user_id = :id")
                    .param("id", userId).query(String.class).single();
            assertThat(digest).hasSize(44).isNotEqualTo(demoCode);
            String incorrect = demoCode.equals("000000") ? "000001" : "000000";
            assertThatThrownBy(() -> otp.verify(userId, incorrect)).isInstanceOf(FormRejectedException.class);
            assertThat(attempts(userId)).isEqualTo(1);

            assertThat(otp.verify(userId, demoCode)).isTrue();
            assertThat(attempts(userId)).isEqualTo(2);
            assertThat(jdbc.sql("SELECT used FROM mobile_otp WHERE user_id = :id")
                    .param("id", userId).query(Boolean.class).single()).isTrue();
            assertThat(currentAccounts.find(userId).orElseThrow().mobileVerified()).isTrue();

            var changed = accounts.changePassword(userId, originalPassword, replacement);
            assertThat(changed.version()).isGreaterThan(citizen.version());
            assertThat(passwords.matches(originalPassword, changed.passwordHash())).isFalse();
            assertThat(passwords.matches(replacement, changed.passwordHash())).isTrue();
        } finally {
            jdbc.sql("DELETE FROM mobile_otp WHERE user_id = :id").param("id", userId).update();
            jdbc.sql("DELETE FROM app_user WHERE id = :id").param("id", userId).update();
        }
    }

    private int attempts(long userId) {
        return jdbc.sql("SELECT attempts FROM mobile_otp WHERE user_id = :id")
                .param("id", userId).query(Integer.class).single();
    }
}
