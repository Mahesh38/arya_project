package com.smartgrievance.complaint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ComplaintNumberGeneratorTest {
    @Test
    @DisplayName("TC-UT-05 complaint numbers display a six-digit sequence")
    void formatsTheYearAndSixDigitSequence() {
        assertThat(ComplaintNumberGenerator.format(2026, 1)).isEqualTo("GRV-2026-000001");
        assertThat(ComplaintNumberGenerator.format(2026, 123)).isEqualTo("GRV-2026-000123");
        assertThat(ComplaintNumberGenerator.format(2026, 999999)).isEqualTo("GRV-2026-999999");
        assertThatIllegalArgumentException().isThrownBy(() -> ComplaintNumberGenerator.format(1999, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> ComplaintNumberGenerator.format(10000, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> ComplaintNumberGenerator.format(2026, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> ComplaintNumberGenerator.format(2026, 1000000));
    }
}
