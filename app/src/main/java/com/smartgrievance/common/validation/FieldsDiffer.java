package com.smartgrievance.common.validation;

import java.lang.annotation.*;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FieldsDifferValidator.class)
public @interface FieldsDiffer {
    String first();
    String second();
    String message() default "{validation.newPassword.sameAsCurrent}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
