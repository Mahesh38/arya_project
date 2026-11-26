package com.smartgrievance.common.validation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {
    private static final Set<String> COMMON = loadCommonPasswords();

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) return true;
        String rule = failedRule(value);
        if (rule == null) return true;
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("{validation.password." + rule + "}")
                .addConstraintViolation();
        return false;
    }

    static String failedRule(String value) {
        int length = value.codePointCount(0, value.length());
        if (length < 8) return "tooShort";
        if (length > 64 || value.getBytes(StandardCharsets.UTF_8).length > 72) return "tooLong";
        if (value.codePoints().noneMatch(Character::isLetter) || value.codePoints().noneMatch(Character::isDigit)) {
            return "letterAndDigit";
        }
        return COMMON.contains(value.toLowerCase(Locale.ROOT)) ? "common" : null;
    }

    private static Set<String> loadCommonPasswords() {
        var resource = Objects.requireNonNull(StrongPasswordValidator.class
                .getResourceAsStream("common-passwords.txt"), "common-passwords.txt");
        try (var reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            return reader.lines().map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(line -> line.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot load common passwords", ex);
        }
    }
}
