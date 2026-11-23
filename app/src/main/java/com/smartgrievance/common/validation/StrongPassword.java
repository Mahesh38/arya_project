package com.smartgrievance.common.validation;

import java.lang.annotation.*;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StrongPasswordValidator.class)
public @interface StrongPassword {
    String message() default "{validation.password.letterAndDigit}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
