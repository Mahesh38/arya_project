package com.smartgrievance.otp;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartgrievance.common.exception.FormRejectedException;
import com.smartgrievance.seed.DemoData;
import com.smartgrievance.testsupport.FlowTestBase;
import com.smartgrievance.user.AccountBootstrap;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/** BR-85, D66: attempts are reserved atomically, so parallel guesses can never exceed the limit. */
@Tag("mysql")
@ActiveProfiles({"test", "mysqltest"})
class OtpConcurrencyMySqlIT extends FlowTestBase {
    @Autowired AccountBootstrap bootstrap;
    @Autowired DemoData users;
    @Autowired OtpService otp;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void seed() {
        bootstrap.ensureAdmin(properties.admin(), false);
        users.seedUsers();
    }

    @Test
    @DisplayName("TC-SEC-37 parallel OTP guesses on MySQL use at most three attempts")
    void parallelGuessesStopAtTheLimit() throws Exception {
        long rahul = jdbc.sql("SELECT id FROM app_user WHERE email = 'rahul@example.com'").query(Long.class).single();
        String code = otp.issueOnRegistration(rahul).demoCode();
        String wrong = code.equals("000000") ? "111111" : "000000";
        int threads = 12;
        var start = new CyclicBarrier(threads);
        var guesses = new ArrayList<Callable<String>>();
        for (int i = 0; i < threads; i++) {
            guesses.add(() -> {
                start.await(10, TimeUnit.SECONDS);
                try {
                    return otp.verify(rahul, wrong) ? "verified" : "not-allowed";
                } catch (FormRejectedException rejected) {
                    return rejected.errors().getFirst().key();
                }
            });
        }
        var outcomes = new ArrayList<String>();
        try (var pool = Executors.newFixedThreadPool(threads)) {
            for (var future : pool.invokeAll(guesses, 60, TimeUnit.SECONDS)) outcomes.add(future.get());
        }
        assertThat(outcomes).doesNotContain("verified");
        assertThat(outcomes.stream().filter("validation.code.incorrect"::equals).count()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT MAX(attempts) FROM mobile_otp WHERE user_id = :id").param("id", rahul)
                .query(Integer.class).single()).isEqualTo(properties.otp().maxAttempts());
        try {
            otp.verify(rahul, code);
        } catch (FormRejectedException expired) {
            assertThat(expired.errors().getFirst().key()).isEqualTo("validation.otp.expired");
        }
        assertThat(jdbc.sql("SELECT mobile_verified FROM app_user WHERE id = :id").param("id", rahul)
                .query(Boolean.class).single()).isFalse();
    }
}
