package com.smartgrievance.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartgrievance.common.validation.FieldsDiffer;
import com.smartgrievance.common.validation.FieldsMatch;
import com.smartgrievance.common.validation.StrongPassword;
import com.smartgrievance.common.validation.ValidMobile;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
class ValidatorsTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();

    @AfterAll
    static void closeFactory() { FACTORY.close(); }

    @ParameterizedTest
    @CsvSource({"short,tooShort", "password1,common", "LongLetterOnly,letterAndDigit"})
    @DisplayName("TC-VE-13 password policy reports only its first failure")
    void strongPassword(String password, String rule) {
        assertThat(FACTORY.getValidator().validate(new PasswordForm(password)))
                .extracting(ConstraintViolation::getMessageTemplate)
                .containsExactly("{validation.password." + rule + "}");
        assertThat(FACTORY.getValidator().validate(new PasswordForm("é".repeat(36) + "1")))
                .extracting(ConstraintViolation::getMessageTemplate).containsExactly("{validation.password.tooLong}");
        assertThat(FACTORY.getValidator().validate(new PasswordForm("Strong8Phrase"))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"9876543210", "6123456789"})
    @DisplayName("TC-VE-13 mobile format allows optional and valid numbers")
    void mobile(String number) {
        assertThat(FACTORY.getValidator().validate(new MobileForm(number))).isEmpty();
        assertThat(FACTORY.getValidator().validate(new MobileForm("5123456789"))).hasSize(1);
    }

    @Test
    @DisplayName("TC-VE-13 field matching attaches the error to the second field")
    void crossFieldValidation() {
        var validator = FACTORY.getValidator();
        assertThat(validator.validate(new MatchForm("same", "same"))).isEmpty();
        assertThat(validator.validate(new MatchForm("same", ""))).isEmpty();
        assertThat(validator.validate(new MatchForm("same", "same "))).singleElement()
                .satisfies(error -> assertThat(error.getPropertyPath().toString()).isEqualTo("second"));
        assertThat(validator.validate(new DifferForm("same", "same"))).singleElement()
                .satisfies(error -> assertThat(error.getPropertyPath().toString()).isEqualTo("second"));
        assertThat(validator.validate(new DifferForm("same", "different"))).isEmpty();
        assertThat(validator.validate(new PasswordForm(null))).isEmpty();
        assertThat(validator.validate(new PasswordForm(""))).isEmpty();
    }

    record PasswordForm(@StrongPassword String password) {}
    record MobileForm(@ValidMobile String mobile) {}

    @FieldsMatch(first = "first", second = "second")
    record MatchForm(String first, String second) {
        public String getFirst() { return first; }
        public String getSecond() { return second; }
    }

    @FieldsDiffer(first = "first", second = "second")
    record DifferForm(String first, String second) {
        public String getFirst() { return first; }
        public String getSecond() { return second; }
    }
}
