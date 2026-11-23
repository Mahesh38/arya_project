package com.smartgrievance.common.validation;

import java.util.regex.Pattern;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidMobileValidator implements ConstraintValidator<ValidMobile, String> {
    private static final Pattern MOBILE = Pattern.compile("[6-9][0-9]{9}");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isEmpty() || MOBILE.matcher(value).matches();
    }
}
