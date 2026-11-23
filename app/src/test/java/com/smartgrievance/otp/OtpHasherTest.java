package com.smartgrievance.otp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import com.smartgrievance.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OtpHasherTest {
    @Test
    @DisplayName("TC-SEC-37a OTP hashes bind the account and process key")
    void hashesAreAccountAndProcessSpecific() {
        var hasher = new OtpHasher();
        var hash = hasher.hash(10, "032761");
        assertThat(hash).hasSize(44).doesNotContain("032761");
        assertThat(hasher.matches(10, "032761", hash)).isTrue();
        assertThat(hasher.matches(11, "032761", hash)).isFalse();
        assertThat(new OtpHasher().matches(10, "032761", hash)).isFalse();
    }
    @Test
    @DisplayName("TC-UT-06 the generated OTPs are six digits and can start with zero")
    void generateCodes() {
        var settings = new AppProperties.Otp("demo", 6, null, 3, null, 5);
        var properties = new AppProperties(null, null, null, null, null, settings, null, null);
        var generator = new OtpCodeGenerator(properties);
        var codes = IntStream.range(0, 10_000).mapToObj(ignored -> generator.next()).toList();
        assertThat(codes).allMatch(code -> code.matches("[0-9]{6}")).anyMatch(code -> code.startsWith("0"));
        assertThat(codes.stream().distinct().count()).isGreaterThan(9_000L);
    }
}
