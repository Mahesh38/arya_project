package com.smartgrievance.common.validation;

import java.lang.annotation.*;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidMobileValidator.class)
public @interface ValidMobile {
    String message() default "{validation.mobile.format}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
